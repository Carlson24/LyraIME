/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.content.Context
import android.os.Build
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.util.FileDownloader
import com.osfans.trime.util.appContext
import com.osfans.trime.util.extractTarGz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object QnnDspManager {
    private val dspDir: File
        get() = File(appContext.filesDir, "qnn-dsp").also { it.mkdirs() }

    data class DspLibs(val stub: File, val skel: File)

    fun getHtpVariant(): String? {
        return when (Build.SOC_MODEL) {
            "SM8350" -> "V68"
            "SM8450", "SM8475" -> "V69"
            "SM8550" -> "V73"
            "SM8650" -> "V75"
            "SM8750" -> "V79"
            "SM8850" -> "V81"
            else -> null
        }
    }

    fun isInstalled(): Boolean {
        val variant = getHtpVariant() ?: return false
        val dir = dspDir
        return File(dir, "libQnnHtp${variant}Stub.so").exists() &&
            File(dir, "libQnnHtp${variant}Skel.so").exists()
    }

    fun getLibs(): DspLibs? {
        val variant = getHtpVariant() ?: return null
        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")
        if (!stub.exists() || !skel.exists()) return null
        return DspLibs(stub, skel)
    }

    suspend fun ensureInstalled(context: Context): DspLibs? = withContext(Dispatchers.IO) {
        val variant = getHtpVariant() ?: return@withContext null
        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")

        if (stub.exists() && skel.exists()) {
            return@withContext DspLibs(stub, skel)
        }

        val entry = ResourceUrls.QNN_DSP_MAP[Build.SOC_MODEL]
            ?: ResourceUrls.QNN_DSP_MAP["SM8850"]!!

        val tarGz = File(context.cacheDir, "qnnDsp-${variant}.tar.gz")
        try {
            FileDownloader.download(entry.url, tarGz, expectedSha256 = entry.sha256)
            extractTarGz(tarGz, dir)
            tarGz.delete()

            if (!stub.exists() || !skel.exists()) {
                Timber.e("QnnDsp: extraction failed for $variant")
                return@withContext null
            }
        } catch (e: FileDownloader.Error) {
            Timber.e(e, "QnnDsp: download failed for $variant")
            tarGz.delete()
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "QnnDsp: failed for $variant")
            tarGz.deleteRecursively()
            return@withContext null
        }

        Timber.i("QnnDsp: $variant installed successfully")
        DspLibs(stub, skel)
    }

    fun loadStub(path: String) {
        System.load(path)
    }
}
