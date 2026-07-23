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
import com.osfans.trime.util.extractTarBz2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object QnnDspManager {
    private val dspDir: File
        get() = File(appContext.filesDir, "qnn-dsp").also { it.mkdirs() }

    data class DspLibs(val stub: File, val skel: File, val htp: File, val system: File)

    fun getHtpVariant(): String? = when (Build.SOC_MODEL) {
        "SM8350" -> "V68"
        "SM8450", "SM8475" -> "V69"
        "SM8550" -> "V73"
        "SM8650" -> "V75"
        "SM8750" -> "V79"
        "SM8850" -> "V81"
        else -> null
    }

    fun isInstalled(): Boolean {
        val variant = getHtpVariant() ?: return false
        val dir = dspDir
        return File(dir, "libQnnHtp${variant}Stub.so").exists() &&
            File(dir, "libQnnHtp${variant}Skel.so").exists() &&
            File(dir, "libQnnHtp.so").exists() &&
            File(dir, "libQnnSystem.so").exists()
    }

    fun getLibs(): DspLibs? {
        val variant = getHtpVariant() ?: return null
        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")
        val htp = File(dir, "libQnnHtp.so")
        val system = File(dir, "libQnnSystem.so")
        if (!stub.exists() || !skel.exists() || !htp.exists() || !system.exists()) return null
        return DspLibs(stub, skel, htp, system)
    }

    suspend fun ensureInstalled(context: Context, force: Boolean = false): DspLibs? = withContext(Dispatchers.IO) {
        val variant = getHtpVariant() ?: return@withContext null
        val dir = dspDir
        val stub = File(dir, "libQnnHtp${variant}Stub.so")
        val skel = File(dir, "libQnnHtp${variant}Skel.so")
        val htp = File(dir, "libQnnHtp.so")
        val system = File(dir, "libQnnSystem.so")

        if (!force && stub.exists() && skel.exists() && htp.exists() && system.exists()) {
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

            val extractedOnnx = File(dir, "libonnxruntime.so")
            if (extractedOnnx.exists()) {
                val onnxDest = File(appContext.filesDir, "onnxruntime/libonnxruntime.so")
                onnxDest.parentFile!!.mkdirs()
                extractedOnnx.renameTo(onnxDest)
                Timber.i("QnnDsp: onnxruntime moved to $onnxDest")
            }

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
        System.load(dsp.stub.absolutePath)
        System.load(dsp.htp.absolutePath)
        System.load(dsp.system.absolutePath)
    }
}
