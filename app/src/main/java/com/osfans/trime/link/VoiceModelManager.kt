/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.computeFileSha256
import timber.log.Timber
import java.io.File

object VoiceModelManager {
    val voiceDir: File
        get() {
            val prefs = AppPrefs.defaultInstance().voiceInput
            val chunkMs = prefs.voiceChunkSize.getValue().ms
            val punct = prefs.voicePunctModel.getValue()
            val base = when (getSelectedVariant()) {
                ModelVariant.INT8 -> "xasr-int8"
                ModelVariant.QNN -> "xasr-qnn"
                else -> "xasr"
            }
            val chunk = "${chunkMs}ms"
            val sub = if (punct) "$chunk-punct" else chunk
            return File(DataManager.voiceDataDir, base).let { File(it, sub).also { it.mkdirs() } }
        }

    enum class ModelVariant {
        STANDARD,
        INT8,
        QNN,
    }

    fun getSelectedVariant(): ModelVariant {
        val pref = AppPrefs.defaultInstance().voiceInput.voiceModelType.getValue()
        return when (pref) {
            AppPrefs.VoiceInput.VoiceModelType.INT8 -> ModelVariant.INT8
            AppPrefs.VoiceInput.VoiceModelType.QNN -> ModelVariant.QNN
            else -> ModelVariant.STANDARD
        }
    }

    private fun voiceModelVariant(): ResourceUrls.VoiceModelVariant = when (getSelectedVariant()) {
        ModelVariant.INT8 -> ResourceUrls.VoiceModelVariant.INT8
        ModelVariant.QNN -> ResourceUrls.VoiceModelVariant.QNN
        else -> ResourceUrls.VoiceModelVariant.STANDARD
    }

    fun getDownloadFileName(): String = getDownloadUrl().substringAfterLast('/')

    fun getDownloadUrl(): String {
        val prefs = AppPrefs.defaultInstance().voiceInput
        val chunkMs = prefs.voiceChunkSize.getValue().ms
        val punct = prefs.voicePunctModel.getValue()
        if (getSelectedVariant() == ModelVariant.QNN) {
            return ResourceUrls.buildQnnVoiceModelUrl(ResourceUrls.resolveQnnSoc(), chunkMs, punct)
        }
        return ResourceUrls.buildVoiceModelUrl(chunkMs, punct, voiceModelVariant())
    }

    suspend fun getExpectedSha256(): String? {
        val url = getDownloadUrl()
        val api = if (getSelectedVariant() == ModelVariant.QNN) {
            ResourceUrls.VOICE_MODEL_QNN_RELEASE_API
        } else {
            ResourceUrls.VOICE_MODEL_RELEASE_API
        }
        return ResourceUrls.GitHubAssetCache.getSha256(url, api)
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
            ResourceUrls.VOICE_MODEL_RELEASE_API,
            ResourceUrls.VOICE_MODEL_QNN_RELEASE_API,
        )
        return knownSha256s.any { hash.equals(it, ignoreCase = true) }
    }

    data class ModelFiles(
        val tokens: File,
        val encoder: File,
        val decoder: File,
        val joiner: File,
        val bpeVocab: File?,
        val isQnn: Boolean = false,
    )

    @Volatile
    private var migrationDone = false

    private fun ensureMigrated() {
        if (migrationDone) return
        synchronized(this) {
            if (migrationDone) return
            val voiceRoot = DataManager.voiceDataDir
            val variants = listOf("xasr", "xasr-int8", "xasr-qnn")
            for (variant in variants) {
                val oldDir = File(voiceRoot, variant)
                if (!oldDir.isDirectory) continue
                val files = oldDir.listFiles { f ->
                    f.isFile &&
                        (
                            f.nameWithoutExtension.startsWith("encoder") ||
                                f.nameWithoutExtension.startsWith("decoder") ||
                                f.nameWithoutExtension.startsWith("joiner") ||
                                f.name == "tokens.txt" ||
                                f.name == "bpe.model"
                            )
                } ?: continue
                if (files.isEmpty()) continue
                // Check whether already has chunk subdirectories
                val subDirs = oldDir.listFiles { f ->
                    f.isDirectory &&
                        (f.name.endsWith("ms") || f.name.endsWith("ms-punct"))
                }
                if (subDirs != null && subDirs.isNotEmpty()) continue
                val targetDir = File(oldDir, "480ms-punct")
                targetDir.mkdirs()
                for (f in files) {
                    val dest = File(targetDir, f.name)
                    if (f.renameTo(dest)) {
                        Timber.i("Voice model migrated: $f -> $dest")
                    } else {
                        Timber.w("Voice model migrate failed: $f")
                    }
                }
                // Clean up leftover non-directory files in oldDir
                oldDir.listFiles { f -> f.isFile }?.forEach { it.delete() }
            }
            migrationDone = true
        }
    }

    fun resolveModelFiles(): ModelFiles? {
        ensureMigrated()
        val dir = voiceDir
        if (!dir.isDirectory) return null

        val isQnn = getSelectedVariant() == ModelVariant.QNN
        val ext = if (isQnn) "bin" else "onnx"
        val label = if (isQnn) "bin" else "onnx"

        val modelFiles = dir.listFiles { f -> f.isFile && f.extension == ext } ?: return null

        val encoder = modelFiles.find { it.nameWithoutExtension.startsWith("encoder") }
        val decoder = modelFiles.find { it.nameWithoutExtension.startsWith("decoder") }
        val joiner = modelFiles.find { it.nameWithoutExtension.startsWith("joiner") }

        val tokensFile = File(dir, "tokens.txt")
        val bpeFile = File(dir, "bpe.model").takeIf { it.exists() }

        if (!tokensFile.exists()) {
            Timber.w("Voice model check: tokens.txt not found")
            return null
        }
        if (encoder == null) {
            Timber.w("Voice model check: encoder $label not found")
            return null
        }
        if (decoder == null) {
            Timber.w("Voice model check: decoder $label not found")
            return null
        }
        if (joiner == null) {
            Timber.w("Voice model check: joiner $label not found")
            return null
        }

        return ModelFiles(tokensFile, encoder, decoder, joiner, bpeFile, isQnn)
    }

    fun checkModelFiles(): Boolean = resolveModelFiles() != null

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
