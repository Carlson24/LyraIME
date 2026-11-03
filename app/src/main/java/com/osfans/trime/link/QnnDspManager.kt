/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.os.Build
import com.osfans.trime.BuildConfig
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.io.File

object QnnDspManager {
    data class DspLibs(
        val stub: File,
        val skel: File,
        val htp: File,
        val system: File,
    )

    /**
     * QNN DSP libraries for the configured [BuildConfig.QNN_VARIANT] variant are
     * pre-packaged in the APK's native lib dir (via the `packageVoiceRuntimeLibs`
     * build task); nothing is downloaded at runtime. When no variant is configured,
     * no DSP libraries are packaged and QNN voice is unavailable.
     */
    private val variant: String? = BuildConfig.QNN_VARIANT.takeIf { it.isNotBlank() }

    private fun packagedDspLibs(): DspLibs? {
        val version = variant?.removePrefix("v") ?: return null
        val libDir = File(appContext.applicationInfo.nativeLibraryDir)
        val stub = File(libDir, "libQnnHtpV${version}Stub.so")
        val skel = File(libDir, "libQnnHtpV${version}Skel.so")
        val htp = File(libDir, "libQnnHtp.so")
        val system = File(libDir, "libQnnSystem.so")
        if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) return null
        return DspLibs(stub, skel, htp, system)
    }

    fun isInstalled(): Boolean = packagedDspLibs() != null

    /** True only when the device SoC is known to be supported by the packaged variant. */
    fun isDeviceSocCompatible(): Boolean {
        val variant = variant ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val rawSoc = Build.SOC_MODEL.orEmpty()
        val soc = QnnSocMatcher.normalize(rawSoc)
        val supported = QnnSocMatcher.isSupported(variant, rawSoc)
        Timber.d(
            "QNN SoC check: raw=%s normalized=%s variant=%s supported=%s",
            rawSoc,
            soc,
            variant,
            supported,
        )
        return supported
    }

    fun isDeviceSupported(): Boolean = isInstalled() && isDeviceSocCompatible()

    fun getLibs(): DspLibs? = packagedDspLibs()

    fun loadDsp(dsp: DspLibs) {
        val version = variant?.removePrefix("v") ?: return
        System.loadLibrary("QnnHtpV${version}Stub")
        System.loadLibrary("QnnHtp")
        System.loadLibrary("QnnSystem")
    }
}
