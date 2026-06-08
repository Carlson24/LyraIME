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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflinePunctuation
import com.k2fsa.sherpa.onnx.OfflinePunctuationConfig
import com.k2fsa.sherpa.onnx.OfflinePunctuationModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.QnnConfig
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object SherpaSpeechClient {
    @Volatile
    var onHoldingChanged: ((Boolean) -> Unit)? = null

    private val recognizerRef = AtomicReference<OfflineRecognizer?>(null)
    private val currentStreamRef = AtomicReference<OfflineStream?>(null)
    private val punctuationRef = AtomicReference<OfflinePunctuation?>(null)

    private val isHolding = AtomicBoolean(false)

    private var audioJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var serviceRef: TrimeInputMethodService? = null
    private val initLock = Any()
    private val audioLock = Any()
    private var isFirstInit = true

    private const val SAMPLE_RATE = 16000
    private const val NOISE_THRESHOLD = 0.02f

    private fun initEngineIfNeeded(): Boolean {
        if (recognizerRef.get() != null) return true

        synchronized(initLock) {
            if (recognizerRef.get() != null) return true

            val voiceDir = DataManager.voiceDataDir
            val metaFile = File(voiceDir, "metadata.json")
            val tokensFile = File(voiceDir, "tokens.txt")

            var numThreads = 4
            var modelName = "model.int8.onnx"
            var punctModelName = "punct.model.int8.onnx"
            var language = "auto"
            var provider = "cpu"
            var decodingMethod = "greedy_search"

            if (metaFile.exists()) {
                try {
                    val jsonString = metaFile.readText(Charsets.UTF_8)
                    val json = JSONObject(jsonString)
                    numThreads = json.optInt("numThreads", numThreads)
                    modelName = json.optString("model", modelName)
                    punctModelName = json.optString("punctModel", punctModelName)
                    language = json.optString("language", language)
                    provider = json.optString("provider", provider)
                    decodingMethod = json.optString("decodingMethod", decodingMethod)
                    Timber.i("Sherpa metadata loaded from external JSON")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse external metadata.json")
                }
            }

            val modelFile = File(voiceDir, modelName)
            if (!modelFile.exists() || !tokensFile.exists()) {
                Timber.e("Sherpa init aborted: model file or tokens.txt not found in voice dir")
                return false
            }
            val punctModelFile = File(voiceDir, punctModelName)
            return try {
                val senseVoiceConfig =
                    OfflineSenseVoiceModelConfig(
                        model = modelFile.absolutePath,
                        language = language,
                        useInverseTextNormalization = true,
                        qnnConfig = QnnConfig(),
                    )

                val modelConfig =
                    OfflineModelConfig(
                        tokens = tokensFile.absolutePath,
                        senseVoice = senseVoiceConfig,
                        debug = false,
                        numThreads = numThreads,
                        provider = provider,
                    )

                val config =
                    OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = modelConfig,
                        decodingMethod = decodingMethod,
                    )

                recognizerRef.set(OfflineRecognizer(null, config))
                Timber.i("Sherpa-onnx offline ASR engine initialized")

                if (punctModelFile.exists()) {
                    try {
                        val punctConfig =
                            OfflinePunctuationConfig(
                                model =
                                OfflinePunctuationModelConfig(
                                    ctTransformer = punctModelFile.absolutePath,
                                    numThreads = numThreads,
                                    debug = false,
                                    provider = provider,
                                ),
                            )
                        punctuationRef.set(OfflinePunctuation(null, punctConfig))
                        Timber.i("Sherpa punctuation model loaded: ${punctModelFile.name}")
                    } catch (e: Throwable) {
                        Timber.e(e, "Sherpa punctuation model failed to load")
                        punctuationRef.set(null)
                    }
                } else {
                    Timber.w("Sherpa punctuation model not found, output will be raw text")
                    punctuationRef.set(null)
                }

                true
            } catch (e: Throwable) {
                Timber.e(e, "Sherpa engine init crashed")
                false
            }
        }
    }

    fun startHoldSession(service: TrimeInputMethodService) {
        if (!isHolding.compareAndSet(false, true)) return
        invokeHoldingChanged(true)

        serviceRef = service

        if (!VoiceModelManager.checkModelFiles()) {
            service.toast(service.getString(R.string.voice_model_not_initialized))
            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
            resetStateDirectly()
            return
        }

        service.lifecycleScope.launch {
            val initWasFirst = isFirstInit
            val startTime = System.currentTimeMillis()
            val initSuccess = withContext(Dispatchers.IO) { initEngineIfNeeded() }
            val elapsed = System.currentTimeMillis() - startTime

            if (initSuccess && initWasFirst) {
                isFirstInit = false
                service.toast("语音引擎加载完成，耗时 ${elapsed}ms")
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
        serviceRef?.let { s ->
            s.lifecycleScope.launch {
                job?.cancelAndJoin()
                s.currentInputConnection?.finishComposingText()
                clearServiceRef()
            }
        } ?: run {
            job?.cancel()
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

                    val chunkBytes = (SAMPLE_RATE * 80 / 1000) * 2
                    val bufSize = minBuf.coerceAtLeast(chunkBytes * 2)

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

                    val chunk = ByteArray(chunkBytes)
                    var notifiedRecordingStarted = false
                    var loopCounter = 0

                    var hasRealAudioEntered = false
                    var continuousSilenceCount = 0
                    val maxTailBufferFrames = 10

                    while (isActive && isHolding.get() && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val n =
                            try {
                                rec.read(chunk, 0, chunk.size)
                            } catch (_: Throwable) {
                                -1
                            }
                        if (n < 0) break
                        if (n == 0) {
                            delay(10)
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
                        val floatChunk = FloatArray(sampleCount)

                        val isCurrentFrameSpeech = amp >= NOISE_THRESHOLD

                        if (isCurrentFrameSpeech) {
                            continuousSilenceCount = 0
                            hasRealAudioEntered = true
                        } else {
                            continuousSilenceCount++
                        }

                        for (i in 0 until sampleCount) {
                            floatChunk[i] = shortChunk[i] / 32768.0f
                        }

                        var pendingText: String? = null

                        synchronized(audioLock) {
                            val engine = recognizerRef.get()
                            val stream = currentStreamRef.get()
                            val puncEngine = punctuationRef.get()

                            if (engine != null && stream != null) {
                                if (hasRealAudioEntered) {
                                    if (continuousSilenceCount <= maxTailBufferFrames) {
                                        stream.acceptWaveform(floatChunk, SAMPLE_RATE)

                                        loopCounter++
                                        if (loopCounter % 8 == 0) {
                                            try {
                                                engine.decode(stream)
                                                val resultObj = engine.getResult(stream)
                                                if (resultObj.text.isNotBlank()) {
                                                    val cleanText = cleanSenseVoiceText(resultObj.text)
                                                    pendingText =
                                                        if (puncEngine != null && cleanText.isNotBlank()) {
                                                            puncEngine.addPunctuation(cleanText)
                                                        } else {
                                                            cleanText
                                                        }
                                                }
                                            } catch (e: Throwable) {
                                                Timber.e(e, "Streaming decode failed")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!pendingText.isNullOrBlank()) {
                            withContext(Dispatchers.Main) {
                                serviceRef?.currentInputConnection?.setComposingText(
                                    pendingText,
                                    1,
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            runCatching { VoiceOverlayUiBridge.onAmplitude?.invoke(amp) }
                        }

                        delay(5)
                    }

                    var finalCleanText: String? = null

                    synchronized(audioLock) {
                        val engine = recognizerRef.get()
                        val stream = currentStreamRef.get()
                        val puncEngine = punctuationRef.get()

                        if (engine != null && stream != null && hasRealAudioEntered) {
                            try {
                                engine.decode(stream)
                                val finalResult = engine.getResult(stream)
                                if (finalResult.text.isNotBlank()) {
                                    val cleanText = cleanSenseVoiceText(finalResult.text)
                                    finalCleanText =
                                        if (puncEngine != null && cleanText.isNotBlank()) {
                                            puncEngine.addPunctuation(cleanText)
                                        } else {
                                            cleanText
                                        }
                                }
                            } catch (e: Throwable) {
                                Timber.e(e, "Final decode failed")
                            }
                        }
                    }

                    if (!finalCleanText.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            serviceRef?.currentInputConnection?.setComposingText(
                                finalCleanText,
                                1,
                            )
                        }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        withContext(Dispatchers.Main) {
                            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                        }
                    } else {
                        Timber.e(t, "Audio recording / inference failed")
                        withContext(Dispatchers.Main) {
                            serviceRef?.let { service.toast(it.getString(R.string.asrkb_err_audio_record_failed)) }
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
                            currentStreamRef.get()?.release()
                        } catch (_: Throwable) {}
                        currentStreamRef.set(null)
                    }
                }
            }
    }

    fun isHolding(): Boolean = isHolding.get()

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
                currentStreamRef.get()?.release()
            } catch (_: Throwable) {}
            currentStreamRef.set(null)
        }
        clearServiceRef()
    }

    private fun invokeHoldingChanged(holding: Boolean) {
        val listener = onHoldingChanged ?: return
        runCatching { listener.invoke(holding) }
            .onFailure { Timber.w(it, "onHoldingChanged failed") }
    }

    private fun clearServiceRef() {
        serviceRef = null
    }

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

    private fun cleanSenseVoiceText(rawText: String): String = rawText.trim()
}
