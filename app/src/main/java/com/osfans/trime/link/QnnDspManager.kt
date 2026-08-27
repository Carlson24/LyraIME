/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.util.appContext
import java.io.File

object QnnDspManager {
    data class DspLibs(
        val stub: File,
        val skel: File,
        val htp: File,
        val system: File,
    )

    /**
     * QNN DSP V81 (SM8850) libraries are pre-packaged in the APK's native lib dir
     * (via the `packageVoiceRuntimeLibs` build task); nothing is downloaded at runtime.
     */
    private fun packagedDspLibs(): DspLibs? {
        val libDir = File(appContext.applicationInfo.nativeLibraryDir)
        val stub = File(libDir, "libQnnHtpV81Stub.so")
        val skel = File(libDir, "libQnnHtpV81Skel.so")
        val htp = File(libDir, "libQnnHtp.so")
        val system = File(libDir, "libQnnSystem.so")
        if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) return null
        return DspLibs(stub, skel, htp, system)
    }

    fun isInstalled(): Boolean = packagedDspLibs() != null

    fun getLibs(): DspLibs? = packagedDspLibs()

    fun loadDsp(dsp: DspLibs) {
        System.loadLibrary("QnnHtpV81Stub")
        System.loadLibrary("QnnHtp")
        System.loadLibrary("QnnSystem")
    }
}