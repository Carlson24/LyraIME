/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.computeFileSha256
import com.osfans.trime.util.extractZip
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object VoiceModelManager {
    const val DOWNLOAD_URL = ResourceUrls.VOICE_MODEL_DOWNLOAD
    const val EXPECTED_SHA256 = ResourceUrls.VOICE_MODEL_SHA256

    val voiceDir: File
        get() = DataManager.voiceDataDir

    fun checkModelFiles(): Boolean {
        val dir = voiceDir
        if (!dir.isDirectory) return false
        if (!File(dir, "tokens.txt").exists()) {
            Timber.w("Voice model check: tokens.txt not found")
            return false
        }
        if (!File(dir, "encoder-480ms.onnx").exists()) {
            Timber.w("Voice model check: encoder-480ms.onnx not found")
            return false
        }
        if (!File(dir, "decoder-480ms.onnx").exists()) {
            Timber.w("Voice model check: decoder-480ms.onnx not found")
            return false
        }
        if (!File(dir, "joiner-480ms.onnx").exists()) {
            Timber.w("Voice model check: joiner-480ms.onnx not found")
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
        val hash = computeFileSha256(file) ?: return false
        return hash.equals(expectedHash, ignoreCase = true)
    }

    fun extractZip(
        zipFile: File,
        targetDir: File,
    ) = com.osfans.trime.util.extractZip(zipFile, targetDir)
}
