/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.os.Build
import timber.log.Timber

/** Acceleration backend used by the built-in (Sherpa) voice engine. */
enum class VoiceBackend {
    /** Qualcomm QNN / HTP DSP acceleration. */
    QNN,

    /** Plain CPU inference (ONNX Runtime). */
    CPU,
}

/**
 * Decides which backend the built-in voice engine should use.
 *
 * QNN is only attempted when the DSP runtime is packaged *and* the device SoC
 * matches the configured [com.osfans.trime.BuildConfig.QNN_VARIANT]. Any
 * initialization failure (wrong HTP arch, missing Skel, driver mismatch, ...)
 * permanently disables QNN for this process so the next session goes straight
 * to CPU.
 */
object VoiceBackendSelector {
    @Volatile
    private var qnnBroken = false

    fun qnnHardwareAvailable(): Boolean = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" &&
        QnnDspManager.isDeviceSupported()

    fun qnnAvailable(): Boolean = qnnHardwareAvailable() && !qnnBroken

    fun preferred(): VoiceBackend = if (qnnAvailable()) VoiceBackend.QNN else VoiceBackend.CPU

    fun isQnnBroken(): Boolean = qnnBroken

    fun markQnnBroken(cause: Throwable? = null) {
        if (qnnBroken) return
        qnnBroken = true
        Timber.w(cause, "QNN backend marked unavailable; CPU fallback will be used")
    }

    /** Allow a new QNN attempt (called when the engine is explicitly reset). */
    fun reset() {
        qnnBroken = false
    }
}
