/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.os.Build
import com.osfans.trime.BuildConfig
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
     * QNN DSP libraries for the configured [BuildConfig.QNN_VARIANT] variant are
     * pre-packaged in the APK's native lib dir (via the `packageVoiceRuntimeLibs`
     * build task); nothing is downloaded at runtime. When no variant is configured,
     * no DSP libraries are packaged and QNN voice is unavailable.
     */
    private val variant: String? = BuildConfig.QNN_VARIANT.takeIf { it.isNotBlank() }

    /**
     * SoCs whose Hexagon DSP arch matches each packaged QNN variant. The HTP
     * backend is variant-specific: loading e.g. the v81 Skel on a v73 device makes
     * QNN initialization fail (or abort in the native layer), so we only attempt
     * QNN when the device SoC is in the whitelist for the configured variant.
     */
    private val variantSocWhitelist =
        mapOf(
            "v81" to setOf("SM8850"),
            "v79" to setOf("SM8750"),
            "v75" to setOf("SM8650"),
            "v73" to setOf("SM8550"),
            "v69" to setOf("SM8450", "SM8475"),
            "v68" to setOf("SM8350"),
            "v66" to setOf("SM8250"),
        )

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
        val whitelist = variantSocWhitelist[variant] ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val soc = Build.SOC_MODEL.trim().uppercase().substringBefore('-').trim()
        if (soc.isEmpty()) return false
        return soc in whitelist
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
