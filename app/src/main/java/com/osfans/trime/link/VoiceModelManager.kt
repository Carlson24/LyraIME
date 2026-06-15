/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.computeFileSha256
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object VoiceModelManager {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    val voiceDir: File
        get() {
            val sub = when (getSelectedVariant()) {
                ModelVariant.INT8 -> "xasr-int8"
                else -> "xasr"
            }
            return File(DataManager.voiceDataDir, sub).also { it.mkdirs() }
        }

    enum class ModelVariant(
        val url: String,
        val sha256: String,
    ) {
        STANDARD(ResourceUrls.VOICE_MODEL_DOWNLOAD, ResourceUrls.VOICE_MODEL_SHA256),
        INT8(ResourceUrls.VOICE_MODEL_INT8_DOWNLOAD, ResourceUrls.VOICE_MODEL_INT8_SHA256),
    }

    fun getSelectedVariant(): ModelVariant {
        val pref = AppPrefs.defaultInstance().localVoice.voiceModelType.getValue()
        return when (pref) {
            AppPrefs.LocalVoice.VoiceModelType.INT8 -> ModelVariant.INT8
            else -> ModelVariant.STANDARD
        }
    }

    fun getDownloadUrl(): String = getSelectedVariant().url

    fun getExpectedSha256(): String = getSelectedVariant().sha256

    fun verifySha256(
        file: File,
        expectedHash: String = getExpectedSha256(),
    ): Boolean {
        val hash = computeFileSha256(file) ?: return false
        return hash.equals(expectedHash, ignoreCase = true)
    }

    fun verifySha256AnyVariant(file: File): Boolean {
        val hash = computeFileSha256(file) ?: return false
        return ModelVariant.entries.any { hash.equals(it.sha256, ignoreCase = true) }
    }

    data class ModelFiles(
        val tokens: File,
        val encoder: File,
        val decoder: File,
        val joiner: File,
        val bpeVocab: File?,
    )

    fun resolveModelFiles(): ModelFiles? {
        val dir = voiceDir
        if (!dir.isDirectory) return null

        val onnxFiles = dir.listFiles { f -> f.isFile && f.extension == "onnx" } ?: return null

        val encoder = onnxFiles.find { it.nameWithoutExtension.startsWith("encoder") }
        val decoder = onnxFiles.find { it.nameWithoutExtension.startsWith("decoder") }
        val joiner = onnxFiles.find { it.nameWithoutExtension.startsWith("joiner") }

        val tokensFile = File(dir, "tokens.txt")
        val bpeFile = File(dir, "bpe.model").takeIf { it.exists() }

        if (!tokensFile.exists()) {
            Timber.w("Voice model check: tokens.txt not found")
            return null
        }
        if (encoder == null) {
            Timber.w("Voice model check: encoder onnx not found")
            return null
        }
        if (decoder == null) {
            Timber.w("Voice model check: decoder onnx not found")
            return null
        }
        if (joiner == null) {
            Timber.w("Voice model check: joiner onnx not found")
            return null
        }

        return ModelFiles(tokensFile, encoder, decoder, joiner, bpeFile)
    }

    fun checkModelFiles(): Boolean = resolveModelFiles() != null

    fun downloadFile(
        urlString: String,
        destFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val request = Request.Builder().url(urlString).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            val body = response.body
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw java.io.IOException("Download cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress?.invoke(totalRead.toFloat() / contentLength)
                        }
                    }
                }
            }
        }
    }

    fun extractZip(
        zipFile: File,
        targetDir: File,
    ) = com.osfans.trime.util.extractZip(zipFile, targetDir)

    fun extractTarBz2(
        tarFile: File,
        targetDir: File,
    ) = com.osfans.trime.util.extractTarBz2(tarFile, targetDir)

    fun autoExtract(
        archiveFile: File,
        targetDir: File,
    ) {
        val name = archiveFile.name.lowercase()
        val isTarBz2 = name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tar.bzip2")
        val isZip = name.endsWith(".zip")

        val format = when {
            isTarBz2 -> "tar.bz2"
            isZip -> "zip"
            else -> detectArchiveFormat(archiveFile)
        }

        when (format) {
            "tar.bz2" -> extractTarBz2(archiveFile, targetDir)
            "zip" -> extractZip(archiveFile, targetDir)
            else -> throw IllegalArgumentException("Unsupported archive format: ${archiveFile.name}")
        }

        cleanupNonModelFiles(targetDir)
    }

    private fun cleanupNonModelFiles(dir: File) {
        val junkNames = setOf("test_wavs", "README.md", "test_onnx.py")
        val junkFiles = dir.listFiles { f -> f.name in junkNames } ?: return
        for (f in junkFiles) {
            f.deleteRecursively()
            Timber.d("Voice model: cleaned up ${f.name}")
        }
    }

    private fun detectArchiveFormat(file: File): String {
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        if (header[0] == 0x42.toByte() && header[1] == 0x5A.toByte() && header[2] == 0x68.toByte()) {
            return "tar.bz2"
        }
        if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return "zip"
        }
        return "unknown"
    }

    fun archiveExtensionFromUrl(url: String): String {
        val name = url.substringAfterLast('/').substringBefore('?').lowercase()
        return when {
            name.endsWith(".tar.bz2") -> ".tar.bz2"
            name.endsWith(".tbz2") -> ".tbz2"
            name.endsWith(".tar.bzip2") -> ".tar.bzip2"
            name.endsWith(".zip") -> ".zip"
            else -> ".tar.bz2"
        }
    }
}
