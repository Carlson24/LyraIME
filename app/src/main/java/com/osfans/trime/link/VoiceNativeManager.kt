/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.os.Build
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import timber.log.Timber

object VoiceNativeManager {
    @Volatile
    private var onnxRuntimeLoaded = false

    @Volatile
    private var qnnDspLoaded = false

    fun loadNativeLibs(): Boolean {
        if (!onnxRuntimeLoaded) {
            try {
                System.loadLibrary("onnxruntime")
                onnxRuntimeLoaded = true
                Timber.i("VoiceNative: bundled onnxruntime loaded")
            } catch (e: Throwable) {
                Timber.e(e, "VoiceNative: failed to load bundled onnxruntime")
                return false
            }
        }

        if (!qnnDspLoaded) {
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
