/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

object VoiceModelManager {
    const val DOWNLOAD_URL = "https://github.com/Carlson24/LyraIME/releases/download/models/Sherpa-Onnx-SenseVoice.zip"
    const val EXPECTED_SHA256 = "cd868696e846586e8216616efd429a7a1ae3c57265d5adc50bde864cca9eb6e5"

    val voiceDir: File
        get() = DataManager.voiceDataDir

    fun checkModelFiles(): Boolean {
        val dir = voiceDir
        if (!dir.isDirectory) return false
        if (!File(dir, "tokens.txt").exists()) {
            Timber.w("Voice model check: tokens.txt not found")
            return false
        }
        val hasModel = dir.listFiles()?.any { it.extension == "onnx" } == true
        if (!hasModel) {
            Timber.w("Voice model check: no .onnx model file found")
            return false
        }
        return true
    }

    fun downloadFile(
        urlString: String,
        destFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30000
            connection.readTimeout = 120000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${connection.responseCode}")
            }
            val contentLength = connection.contentLength.toLong()
            connection.inputStream.use { input ->
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
        } finally {
            connection.disconnect()
        }
    }

    fun verifySha256(
        file: File,
        expectedHash: String = EXPECTED_SHA256,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(expectedHash, ignoreCase = true)
    }

    fun extractZip(
        zipFile: File,
        targetDir: File,
    ) {
        targetDir.mkdirs()

        val zip = ZipFile(zipFile)
        val entries = zip.entries().asSequence().toList()
        val prefix = findCommonPrefix(entries.map { it.name.trim('/') })

        for (entry in entries) {
            val name = entry.name.trim('/')
            var relativePath = name
            if (prefix != null && name.startsWith(prefix)) {
                relativePath = name.removePrefix(prefix).trim('/')
            }
            if (relativePath.isEmpty()) continue

            val destFile = File(targetDir, relativePath)
            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        zip.close()
    }

    private fun findCommonPrefix(entries: List<String>): String? {
        if (entries.size <= 1) return null
        val first = entries.first()
        val slashIndex = first.indexOf('/')
        if (slashIndex < 0) return null
        val candidatePrefix = first.substring(0, slashIndex + 1)
        if (entries.all { it == candidatePrefix.trim('/') || it.startsWith(candidatePrefix) }) {
            return candidatePrefix
        }
        return null
    }
}
