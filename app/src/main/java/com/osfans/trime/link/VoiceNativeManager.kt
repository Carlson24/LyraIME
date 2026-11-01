/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

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

        // DSP libs are only loaded on devices whose SoC matches the packaged QNN
        // variant; a failed load must not break CPU-only inference.
        if (!qnnDspLoaded && !VoiceBackendSelector.isQnnBroken() && QnnDspManager.isDeviceSupported()) {
            try {
                val dsp = QnnDspManager.getLibs()
                if (dsp != null) {
                    QnnDspManager.loadDsp(dsp)
                    OnlineRecognizer.prependAdspLibraryPath(dsp.skel.parentFile!!.absolutePath)
                    qnnDspLoaded = true
                    Timber.i("VoiceNative: QNN DSP loaded")
                }
            } catch (e: Throwable) {
                VoiceBackendSelector.markQnnBroken(e)
            }
        }

        return true
    }
}
