/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.appContext
import com.osfans.trime.util.computeFileSha256
import timber.log.Timber
import java.io.File

object VoiceModelManager {
    /**
     * QNN-only local voice. The model .so libs must be dlopen'd; external/shared
     * storage is noexec, so keep the QNN model on internal storage. Punctuation is
     * always enabled and there is only one model kind, so the directory is simply
     * `filesDir/voice-qnn/<chunk>ms`.
     */
    val voiceDir: File
        get() {
            val prefs = AppPrefs.defaultInstance().voiceInput
            val chunkMs = prefs.voiceChunkSize.getValue().ms
            return File(appContext.filesDir, "voice-qnn")
                .let { File(it, "${chunkMs}ms").also { dir -> dir.mkdirs() } }
        }

    fun getDownloadFileName(): String = getDownloadUrl().substringAfterLast('/')

    fun getDownloadUrl(): String {
        val prefs = AppPrefs.defaultInstance().voiceInput
        val chunkMs = prefs.voiceChunkSize.getValue().ms
        return ResourceUrls.buildQnnVoiceModelUrl(chunkMs)
    }

    suspend fun getExpectedSha256(): String? {
        val url = getDownloadUrl()
        return ResourceUrls.GitHubAssetCache.getSha256(
            url,
            ResourceUrls.VOICE_MODEL_QNN_RELEASE_API,
        )
    }

    fun verifySha256(
        file: File,
        expectedHash: String?,
    ): Boolean {
        if (expectedHash == null) return false
        val hash = computeFileSha256(file) ?: return false
        return hash.equals(expectedHash, ignoreCase = true)
    }

    suspend fun verifySha256AnyVariant(file: File): Boolean {
        val hash = computeFileSha256(file) ?: return false
        val knownSha256s = ResourceUrls.GitHubAssetCache.getAllKnownSha256s(
            ResourceUrls.VOICE_MODEL_QNN_RELEASE_API,
        )
        return knownSha256s.any { hash.equals(it, ignoreCase = true) }
    }

    data class ModelFiles(
        val tokens: File,
        val encoder: File,
        val decoder: File,
        val joiner: File,
    ) {
        /** QNN context binary paths; generated on-device on first use. */
        val qnnContextBinary: String
            get() {
                val dir = encoder.parentFile!!
                return listOf(encoder, decoder, joiner)
                    .joinToString(",") {
                        "${dir.absolutePath}/${it.nameWithoutExtension.removePrefix("lib")}.bin"
                    }
            }
    }

    private fun contextBinaryFiles(dir: File): List<File> = listOf("encoder.bin", "decoder.bin", "joiner.bin").map { File(dir, it) }

    fun hasContextBinaries(): Boolean {
        val dir = voiceDir
        if (!dir.isDirectory) return false
        return contextBinaryFiles(dir).all { it.isFile }
    }

    fun resolveModelFiles(): ModelFiles? {
        val dir = voiceDir
        if (!dir.isDirectory) return null

        val tokensFile = File(dir, "tokens.txt")
        if (!tokensFile.exists()) {
            Timber.w("Voice model check: tokens.txt not found")
            return null
        }

        val libEncoder = File(dir, "libencoder.so")
        val libDecoder = File(dir, "libdecoder.so")
        val libJoiner = File(dir, "libjoiner.so")
        val hasLibs = libEncoder.isFile && libDecoder.isFile && libJoiner.isFile

        if (!hasLibs && !hasContextBinaries()) {
            Timber.w("Voice model check: neither .so model libs nor .bin context binaries found")
            return null
        }

        return if (hasLibs) {
            ModelFiles(tokensFile, libEncoder, libDecoder, libJoiner)
        } else {
            val bins = contextBinaryFiles(dir)
            ModelFiles(tokensFile, bins[0], bins[1], bins[2])
        }
    }

    fun checkModelFiles(): Boolean = resolveModelFiles() != null

    /**
     * Delete the original .so model libs once the QNN context binaries have been
     * generated on-device. No-op (returns false) unless all three .bin files exist,
     * so a failed context binary save keeps the .so fallback.
     */
    fun deleteModelLibsIfContextBinaryReady(): Boolean {
        if (!hasContextBinaries()) return false
        val dir = voiceDir
        val results =
            listOf("libencoder.so", "libdecoder.so", "libjoiner.so").map {
                val f = File(dir, it)
                !f.exists() || f.delete()
            }
        if (results.any { !it }) {
            Timber.w("Voice model: failed to delete some .so model libs")
        }
        return results.all { it }
    }

    /** Delete the current chunk's model files (other chunk variants are kept). */
    fun deleteModel(): Boolean = voiceDir.deleteRecursively()

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
