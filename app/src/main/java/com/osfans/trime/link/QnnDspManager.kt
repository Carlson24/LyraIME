/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.content.Context
import android.os.Build
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.util.FileDownloader
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.extractTarBz2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object QnnDspManager {
    private val dspDir: File
        get() = File(appContext.filesDir, "qnn-dsp").also { it.mkdirs() }

    data class DspLibs(
        val stub: File,
        val skel: File,
        val htp: File,
        val system: File,
        val packaged: Boolean = false,
    )

    fun getHtpVariant(): String? = when (Build.SOC_MODEL) {
        "SM8350" -> "V68"
        "SM8450", "SM8475" -> "V69"
        "SM8550" -> "V73"
        "SM8650" -> "V75"
        "SM8750" -> "V79"
        "SM8850" -> "V81"
        else -> null
    }

    /** V81 (SM8850) libs are pre-packaged in the APK's native lib dir. */
    private fun packagedDspLibs(): DspLibs? {
        val libDir = File(appContext.applicationInfo.nativeLibraryDir)
        val stub = File(libDir, "libQnnHtpV81Stub.so")
        val skel = File(libDir, "libQnnHtpV81Skel.so")
        val htp = File(libDir, "libQnnHtp.so")
        val system = File(libDir, "libQnnSystem.so")
        if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) return null
        return DspLibs(stub, skel, htp, system, packaged = true)
    }

    private fun downloadedDspLibs(variant: String): DspLibs? {
        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")
        val htp = File(dir, "libQnnHtp.so")
        val system = File(dir, "libQnnSystem.so")
        if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) return null
        return DspLibs(stub, skel, htp, system)
    }

    fun isInstalled(): Boolean {
        val variant = getHtpVariant() ?: return false
        if (variant == "V81") {
            return packagedDspLibs() != null
        }
        return downloadedDspLibs(variant) != null
    }

    fun getLibs(): DspLibs? {
        val variant = getHtpVariant() ?: return null
        if (variant == "V81") {
            packagedDspLibs()?.let { return it }
            Timber.w("QnnDsp: V81 packaged libs not found, falling back to downloaded libs")
        }
        return downloadedDspLibs(variant)
    }

    suspend fun ensureInstalled(context: Context, force: Boolean = false): DspLibs? = withContext(Dispatchers.IO) {
        val variant = getHtpVariant() ?: return@withContext null
        if (variant == "V81") {
            return@withContext packagedDspLibs()
        }

        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")
        val htp = File(dir, "libQnnHtp.so")
        val system = File(dir, "libQnnSystem.so")

        if (!force && stub.exists() && skel.exists() && htp.exists() && system.exists()) {
            FileUtils.markNativeLibsReadOnly(dir)
            return@withContext DspLibs(stub, skel, htp, system)
        }

        dir.listFiles()?.forEach { it.deleteRecursively() }

        val entry = ResourceUrls.QNN_DSP_MAP[Build.SOC_MODEL]
            ?: ResourceUrls.QNN_DSP_MAP["SM8850"]!!

        val expectedSha256 = ResourceUrls.GitHubAssetCache.getSha256(
            entry.url,
            ResourceUrls.QNN_DSP_RELEASE_API,
        )

        val tarBz2 = File(context.cacheDir, "qnnDsp-$variant.tar.bz2")
        try {
            FileDownloader.download(entry.url, tarBz2, expectedSha256 = expectedSha256)
            extractTarBz2(tarBz2, dir)
            FileUtils.markNativeLibsReadOnly(dir)
            tarBz2.delete()

            if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) {
                Timber.e("QnnDsp: extraction failed for $variant")
                return@withContext null
            }
        } catch (e: FileDownloader.Error) {
            Timber.e(e, "QnnDsp: download failed for $variant")
            tarBz2.delete()
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "QnnDsp: failed for $variant")
            tarBz2.deleteRecursively()
            return@withContext null
        }

        Timber.i("QnnDsp: $variant installed successfully")
        DspLibs(stub, skel, htp, system)
    }

    fun loadDsp(dsp: DspLibs) {
        if (dsp.packaged) {
            System.loadLibrary("QnnHtpV81Stub")
            System.loadLibrary("QnnHtp")
            System.loadLibrary("QnnSystem")
        } else {
            dsp.stub.setWritable(false, false)
            dsp.htp.setWritable(false, false)
            dsp.system.setWritable(false, false)
            System.load(dsp.stub.absolutePath)
            System.load(dsp.htp.absolutePath)
            System.load(dsp.system.absolutePath)
        }
    }
}