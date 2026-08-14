/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.content.Context
import android.os.Build
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.util.FileDownloader
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.extractTarBz2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object VoiceNativeManager {
    @Volatile
    private var onnxRuntimeLoaded = false

    @Volatile
    private var qnnDspLoaded = false

    fun onnxRuntimeLibPath(): File = File(appContext.filesDir, "onnxruntime/libonnxruntime.so")

    fun isOnnxRuntimeInstalled(): Boolean = onnxRuntimeLibPath().exists()

    suspend fun ensureOnnxRuntime(context: Context): Boolean {
        if (isOnnxRuntimeInstalled()) {
            FileUtils.markNativeLibsReadOnly(onnxRuntimeLibPath().parentFile!!)
            return true
        }

        val tmp = File(context.cacheDir, "onnx-runtime.tar.bz2")
        return try {
            withContext(Dispatchers.IO) {
                FileDownloader.download(ResourceUrls.ONNX_RUNTIME_URL, tmp)
                val onnxDir = onnxRuntimeLibPath().parentFile!!
                onnxDir.mkdirs()
                extractTarBz2(tmp, onnxDir)
                FileUtils.markNativeLibsReadOnly(onnxDir)
                tmp.delete()
            }
            val installed = isOnnxRuntimeInstalled()
            if (installed) {
                Timber.i("VoiceNative: onnxruntime installed")
            } else {
                Timber.e("VoiceNative: onnxruntime not found after extraction")
            }
            installed
        } catch (e: Exception) {
            Timber.e(e, "VoiceNative: onnxruntime install failed")
            tmp.delete()
            false
        }
    }

    fun loadNativeLibs(useQnn: Boolean): Boolean {
        if (!onnxRuntimeLoaded) {
            val so = onnxRuntimeLibPath()
            if (!so.exists()) return false
            so.setWritable(false, false)
            System.load(so.absolutePath)
            onnxRuntimeLoaded = true
            Timber.i("VoiceNative: onnxruntime loaded")
        }

        if (useQnn && !qnnDspLoaded) {
            if (Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") {
                val dsp = QnnDspManager.getLibs()
                if (dsp != null) {
                    QnnDspManager.loadDsp(dsp)
                    OnlineRecognizer.prependAdspLibraryPath(dsp.skel.parentFile!!.absolutePath)
                    qnnDspLoaded = true
                    Timber.i("VoiceNative: QNN DSP loaded")
                }
            }
        }

        return true
    }
}
