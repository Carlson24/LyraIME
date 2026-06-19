/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.os.Build
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.QnnConfig
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

object SherpaSpeechClient {
    @Volatile
    var onHoldingChanged: ((Boolean) -> Unit)? = null

    @Volatile
    private var adspLibraryPathSet = false

    private val recognizerRef = AtomicReference<OnlineRecognizer?>(null)
    private val currentStreamRef = AtomicReference<OnlineStream?>(null)

    private data class EngineConfig(
        val modelType: AppPrefs.LocalVoice.VoiceModelType,
        val numThreads: Int,
        val sensitivity: Int,
    ) {
        val useQnn: Boolean get() = modelType == AppPrefs.LocalVoice.VoiceModelType.QNN
    }

    @Volatile
    private var lastEngineConfig: EngineConfig? = null

    @Volatile
    private var engineWasReloaded = false

    private val isHolding = AtomicBoolean(false)

    private var audioJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var serviceRef: WeakReference<TrimeInputMethodService>? = null
    private val initLock = Any()
    private val audioLock = Any()
    private var isFirstInit = true

    private const val SAMPLE_RATE = 16000
    private const val NOISE_THRESHOLD = 0.02f
    private const val CHUNK_MS = 480
    private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
    private const val CHUNK_BYTES = CHUNK_SAMPLES * 2
    private const val PARTIAL_EMIT_MIN_INTERVAL_MS = 120L

    @Volatile
    private var lastEmitUptimeMs: Long = 0L

    @Volatile
    private var lastEmittedText: String? = null

    @Volatile
    private var lastRawText: String? = null

    fun prewarmAsync(service: TrimeInputMethodService) {
        if (!VoiceModelManager.checkModelFiles()) return
        val currentConfig = readEngineConfig()
        if (!currentConfig.useQnn) return
        service.lifecycleScope.launch(Dispatchers.IO) {
            if (!adspLibraryPathSet && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") {
                val nativeLibDir = service.applicationInfo.nativeLibraryDir
                if (nativeLibDir.isNotEmpty()) {
                    OnlineRecognizer.prependAdspLibraryPath(nativeLibDir)
                    adspLibraryPathSet = true
                }
            }
            initEngineIfNeeded()
        }
    }

    private fun initEngineIfNeeded(): Boolean {
        val currentConfig = readEngineConfig()

        synchronized(initLock) {
            if (recognizerRef.get() != null && currentConfig == lastEngineConfig) return true

            if (recognizerRef.get() != null) {
                Timber.i("Engine config changed ($lastEngineConfig -> $currentConfig), releasing old recognizer")
                try {
                    recognizerRef.getAndSet(null)?.release()
                } catch (_: Throwable) {}
                engineWasReloaded = true
            }

            val modelFiles = VoiceModelManager.resolveModelFiles()
            if (modelFiles == null) {
                Timber.e("Sherpa init aborted: model files not resolved in voice dir")
                engineWasReloaded = false
                return false
            }

            return try {
                val isArm64 = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
                val effectiveQnn = modelFiles.isQnn && isArm64

                val transducerConfig =
                    if (effectiveQnn) {
                        OnlineTransducerModelConfig(
                            encoder = "",
                            decoder = "",
                            joiner = "",
                            qnnConfig = QnnConfig(
                                backendLib = "libQnnHtp.so",
                                systemLib = "libQnnSystem.so",
                                contextBinary = "${modelFiles.encoder.absolutePath}," +
                                    "${modelFiles.decoder.absolutePath}," +
                                    "${modelFiles.joiner.absolutePath}",
                            ),
                        )
                    } else {
                        OnlineTransducerModelConfig(
                            encoder = modelFiles.encoder.absolutePath,
                            decoder = modelFiles.decoder.absolutePath,
                            joiner = modelFiles.joiner.absolutePath,
                        )
                    }

                val useBpe = modelFiles.bpeVocab != null

                val modelConfig =
                    OnlineModelConfig(
                        transducer = transducerConfig,
                        tokens = modelFiles.tokens.absolutePath,
                        numThreads = if (effectiveQnn) 1 else currentConfig.numThreads,
                        provider = if (effectiveQnn) "qnn" else "cpu",
                        modelType = if (effectiveQnn) "zipformer" else "",
                        debug = false,
                        modelingUnit = "",
                        bpeVocab = modelFiles.bpeVocab?.absolutePath ?: "",
                    )

                val config =
                    OnlineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = modelConfig,
                        decodingMethod = "greedy_search",
                        maxActivePaths = currentConfig.sensitivity,
                        enableEndpoint = false,
                    )

                val encoderPath = modelFiles.encoder.absolutePath
                val engineVariant = when {
                    effectiveQnn -> "qnn"
                    modelFiles.isQnn -> "qnn-fallback-cpu"
                    modelFiles.encoder.nameWithoutExtension.contains("int8", ignoreCase = true) -> "int8"
                    else -> "standard"
                }
                Timber.i("Creating OnlineRecognizer: encoder=$encoderPath, bpe=$useBpe, variant=$engineVariant")
                recognizerRef.set(OnlineRecognizer(null, config))
                lastEngineConfig = currentConfig
                Timber.i("Sherpa-onnx online ASR engine initialized (bpe=$useBpe, variant=$engineVariant)")
                true
            } catch (e: Throwable) {
                Timber.e(e, "Sherpa engine init crashed")
                engineWasReloaded = false
                false
            }
        }
    }

    fun startHoldSession(service: TrimeInputMethodService) {
        if (!isHolding.compareAndSet(false, true)) return
        invokeHoldingChanged(true)

        serviceRef = WeakReference(service)
        lastEmitUptimeMs = 0L
        lastEmittedText = null
        lastRawText = null

        if (!VoiceModelManager.checkModelFiles()) {
            service.toast(service.getString(R.string.voice_model_not_initialized))
            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
            resetStateDirectly()
            return
        }

        service.lifecycleScope.launch {
            val initWasFirst = isFirstInit
            val currentConfig = readEngineConfig()

            if (lastEngineConfig != null && currentConfig != lastEngineConfig) {
                service.toast(service.getString(R.string.voice_engine_reloading))
            }

            val startTime = System.currentTimeMillis()
            val initSuccess = withContext(Dispatchers.IO) {
                if (!adspLibraryPathSet && currentConfig.useQnn &&
                    Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
                ) {
                    val nativeLibDir = service.applicationInfo.nativeLibraryDir
                    if (nativeLibDir.isNotEmpty()) {
                        OnlineRecognizer.prependAdspLibraryPath(nativeLibDir)
                        adspLibraryPathSet = true
                    }
                }
                initEngineIfNeeded()
            }
            val elapsed = System.currentTimeMillis() - startTime

            if (initSuccess && initWasFirst) {
                isFirstInit = false
                service.toast(service.getString(R.string.voice_engine_loaded, elapsed))
            }

            if (initSuccess && engineWasReloaded) {
                engineWasReloaded = false
                service.toast(service.getString(R.string.voice_engine_reloaded, elapsed))
            }

            if (initSuccess) {
                val engine = recognizerRef.get()
                if (isHolding.get() && engine != null) {
                    try {
                        synchronized(audioLock) {
                            val newStream = engine.createStream()
                            currentStreamRef.set(newStream)
                        }
                        startAudioStreaming(service)
                    } catch (t: Throwable) {
                        Timber.e(t, "Failed to create inference stream")
                        cancelSession()
                    }
                }
            } else {
                service.lifecycleScope.launch {
                    service.toast(service.getString(R.string.asrkb_err_audio_record_failed))
                }
                cancelSession()
            }
        }
    }

    fun stopHoldSession() {
        if (!isHolding.compareAndSet(true, false)) return
        invokeHoldingChanged(false)
        val job = audioJob
        audioJob = null
        if (job == null) {
            clearServiceRef()
        }
    }

    private fun startAudioStreaming(service: TrimeInputMethodService) {
        if (
            ContextCompat.checkSelfPermission(
                service,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val intent =
                Intent(service, MicPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            runCatching { service.startActivity(intent) }
            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
            resetStateDirectly()
            return
        }

        audioJob =
            service.lifecycleScope.launch(Dispatchers.IO) {
                var rec: AudioRecord? = null
                try {
                    val ch = AudioFormat.CHANNEL_IN_MONO
                    val fmt = AudioFormat.ENCODING_PCM_16BIT
                    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, ch, fmt)

                    val bufSize = minBuf.coerceAtLeast(CHUNK_BYTES * 2)

                    rec =
                        listOf(
                            MediaRecorder.AudioSource.MIC,
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        ).firstNotNullOfOrNull { source ->
                            runCatching {
                                AudioRecord(source, SAMPLE_RATE, ch, fmt, bufSize)
                            }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                        }
                    if (rec == null) {
                        Timber.e("Failed to create AudioRecord instance")
                        return@launch
                    }

                    audioRecord = rec
                    rec.startRecording()

                    val chunk = ByteArray(CHUNK_BYTES)
                    var notifiedRecordingStarted = false

                    var loopCounter = 0L

                    while (isActive && isHolding.get() && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val n =
                            try {
                                rec.read(chunk, 0, chunk.size)
                            } catch (_: Throwable) {
                                -1
                            }
                        if (n < 0) break
                        if (n == 0) {
                            delay(10.milliseconds)
                            continue
                        }

                        if (!notifiedRecordingStarted) {
                            notifiedRecordingStarted = true
                            withContext(Dispatchers.Main) {
                                runCatching { VoiceOverlayUiBridge.onRecordingStarted?.invoke() }
                            }
                        }

                        val sampleCount = n / 2
                        val shortChunk = ShortArray(sampleCount)
                        ByteBuffer.wrap(chunk, 0, n)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortChunk)

                        val amp = calculateAmplitude(chunk, n)
                        val isCurrentFrameSpeech = amp >= NOISE_THRESHOLD
                        val floatChunk = FloatArray(sampleCount)

                        for (i in 0 until sampleCount) {
                            floatChunk[i] = shortChunk[i] / 32768.0f
                        }

                        var rawText: String? = null

                        synchronized(audioLock) {
                            val engine = recognizerRef.get()
                            val stream = currentStreamRef.get()

                            if (engine != null && stream != null) {
                                stream.acceptWaveform(floatChunk, SAMPLE_RATE)

                                var loops = 0
                                while (engine.isReady(stream) && loops < 64) {
                                    engine.decode(stream)
                                    loops++
                                }

                                val resultObj = engine.getResult(stream)
                                if (resultObj.text.isNotBlank()) {
                                    rawText = resultObj.text
                                }
                            }
                        }

                        if (!rawText.isNullOrBlank() && rawText != lastRawText) {
                            lastRawText = rawText
                            val now = SystemClock.uptimeMillis()
                            if ((now - lastEmitUptimeMs) >= PARTIAL_EMIT_MIN_INTERVAL_MS) {
                                val normalized = normalizeCjkSpacing(rawText)
                                if (normalized.isNotEmpty() && normalized != lastEmittedText) {
                                    lastEmittedText = normalized
                                    lastEmitUptimeMs = now
                                    withContext(Dispatchers.Main) {
                                        serviceRef?.get()?.currentInputConnection?.setComposingText(
                                            normalized,
                                            1,
                                        )
                                    }
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            runCatching { VoiceOverlayUiBridge.onAmplitude?.invoke(amp) }
                        }
                        loopCounter++

                        delay(5.milliseconds)
                    }

                    var finalText: String? = null

                    synchronized(audioLock) {
                        val engine = recognizerRef.get()
                        val stream = currentStreamRef.get()

                        if (engine != null && stream != null) {
                            try {
                                val tailSamples = ((SAMPLE_RATE * 480 / 1000)).coerceAtLeast(1)
                                val tail = FloatArray(tailSamples)
                                stream.acceptWaveform(tail, SAMPLE_RATE)
                                stream.inputFinished()

                                val startUptimeMs = SystemClock.uptimeMillis()
                                val maxUptimeMs = startUptimeMs + 2500L
                                var loops = 0
                                while (loops < 512 && SystemClock.uptimeMillis() < maxUptimeMs) {
                                    if (!engine.isReady(stream)) break
                                    engine.decode(stream)
                                    loops++
                                }

                                val finalResult = engine.getResult(stream)
                                if (finalResult.text.isNotBlank()) {
                                    finalText = normalizeCjkSpacing(finalResult.text)
                                }
                            } catch (e: Throwable) {
                                Timber.e(e, "Final decode failed")
                            }
                        }
                    }

                    if (!finalText.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            val ic = serviceRef?.get()?.currentInputConnection
                            ic?.setComposingText(finalText, 1)
                            ic?.finishComposingText()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                        clearServiceRef()
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        withContext(Dispatchers.Main) {
                            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                        }
                    } else {
                        Timber.e(t, "Audio recording / inference failed")
                        withContext(Dispatchers.Main) {
                            serviceRef?.get()?.let { service.toast(it.getString(R.string.asrkb_err_audio_record_failed)) }
                            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                        }
                    }
                } finally {
                    try {
                        rec?.stop()
                        rec?.release()
                    } catch (_: Throwable) {}
                    if (audioRecord == rec) {
                        audioRecord = null
                    }
                    synchronized(audioLock) {
                        try {
                            currentStreamRef.getAndSet(null)?.release()
                        } catch (_: Throwable) {}
                    }
                }
            }
    }

    fun isHolding(): Boolean = isHolding.get()

    private fun readEngineConfig(): EngineConfig {
        val prefs = AppPrefs.defaultInstance().localVoice
        return EngineConfig(
            modelType = prefs.voiceModelType.getValue(),
            numThreads = prefs.voiceNumThreads.getValue()
                .coerceIn(1, Runtime.getRuntime().availableProcessors()),
            sensitivity = prefs.voiceSensitivity.getValue()
                .coerceIn(1, 10),
        )
    }

    private fun cancelSession() {
        isHolding.set(false)
        audioJob?.cancel()
        audioJob = null
        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
        resetStateDirectly()
    }

    private fun resetStateDirectly() {
        isHolding.set(false)
        invokeHoldingChanged(false)
        synchronized(audioLock) {
            try {
                currentStreamRef.getAndSet(null)?.release()
            } catch (_: Throwable) {}
        }
        clearServiceRef()
    }

    private fun invokeHoldingChanged(holding: Boolean) {
        val listener = onHoldingChanged ?: return
        runCatching { listener.invoke(holding) }
            .onFailure { Timber.w(it, "onHoldingChanged failed") }
    }

    private fun clearServiceRef() {
        serviceRef?.clear()
        serviceRef = null
    }

    private fun normalizeCjkSpacing(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val chars = trimmed.toCharArray()
        val out = StringBuilder(trimmed.length)
        var i = 0
        while (i < chars.size) {
            val ch = chars[i]
            if (ch.isWhitespace()) {
                val prev = out.lastOrNull()
                var j = i + 1
                while (j < chars.size && chars[j].isWhitespace()) {
                    j++
                }
                val next = chars.getOrNull(j)
                val dropCjkSpace = prev != null &&
                    next != null &&
                    isCjkOrCjkPunct(prev) &&
                    isCjkOrCjkPunct(next)
                val dropBeforeAsciiPunct = next != null &&
                    next in ".,!?;:%)]}"
                if (!dropCjkSpace && !dropBeforeAsciiPunct) {
                    var k = i
                    while (k < j) {
                        out.append(chars[k])
                        k++
                    }
                }
                i = j
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

    private fun isCjkOrCjkPunct(ch: Char): Boolean = ch in '\u3400'..'\u4DBF' ||
        ch in '\u4E00'..'\u9FFF' ||
        ch in '\uF900'..'\uFAFF' ||
        ch in '！'..'～' ||
        ch in '\u3000'..'\u303F' ||
        ch in '\uFF00'..'\uFFEF' ||
        ch in '\uFE30'..'\uFE4F'

    private fun calculateAmplitude(buffer: ByteArray, size: Int): Float {
        var max = 0
        for (i in 0 until size step 2) {
            if (i + 1 >= size) break
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            val absSample = kotlin.math.abs(sample)
            if (absSample > max) max = absSample
        }
        return max.toFloat() / 32768f
    }
}
