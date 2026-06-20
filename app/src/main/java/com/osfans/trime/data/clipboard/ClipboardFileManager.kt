// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.InputStream

class ClipboardFileManager(private val context: Context) {

    companion object {
        private const val TAG = "ClipboardFileManager"
        private const val CLIPBOARD_FOLDER = "clipboard"
        private const val MAX_CACHE_SIZE_MB = 500
        private const val MAX_FILE_AGE_DAYS = 30
    }

    private fun getClipboardFolder(): File {
        val dir = File(context.filesDir, CLIPBOARD_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getFile(fileName: String): File = File(getClipboardFolder(), fileName)

    fun fileExists(fileName: String, expectedSize: Long? = null): Boolean {
        val file = getFile(fileName)
        if (!file.exists() || !file.isFile) {
            return false
        }
        if (expectedSize != null && expectedSize > 0) {
            val actualSize = file.length()
            if (actualSize != expectedSize) {
                Timber.tag(TAG).w(
                    "File size mismatch: $fileName (expected: $expectedSize, actual: $actualSize)",
                )
                return false
            }
        }
        return true
    }

    fun saveFile(
        fileName: String,
        inputStream: InputStream,
        totalBytes: Long = -1,
        progressCallback: ((Long, Long) -> Unit)? = null,
    ): String? = try {
        val file = getFile(fileName)
        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.delete()
        }

        var downloadedBytes = 0L
        val buffer = ByteArray(8192)

        file.outputStream().use { output ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                progressCallback?.invoke(downloadedBytes, totalBytes)
            }
        }

        Timber.tag(TAG).d("File saved: $fileName -> ${file.absolutePath}")
        file.absolutePath
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to save file: $fileName")
        null
    } finally {
        try {
            inputStream.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to close input stream")
        }
    }

    fun deleteFile(fileName: String): Boolean = try {
        val file = getFile(fileName)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to delete file: $fileName")
        false
    }

    fun cleanOldFiles(): Int {
        val cutoffTime = System.currentTimeMillis() - MAX_FILE_AGE_DAYS * 24 * 60 * 60 * 1000L
        var count = 0

        try {
            val dir = getClipboardFolder()
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        count++
                        Timber.tag(TAG).d("Deleted old file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clean old files")
        }

        return count
    }

    fun ensureCacheLimit(): Int {
        var count = 0

        try {
            val dir = getClipboardFolder()
            val files =
                dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
                    ?: return 0

            var totalSize = files.sumOf { it.length() }
            val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024L

            for (file in files) {
                if (totalSize <= maxSize) break
                totalSize -= file.length()
                if (file.delete()) {
                    count++
                    Timber.tag(TAG).d("Deleted file to free space: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to ensure cache limit")
        }

        return count
    }

    fun getCacheSize(): Long = try {
        val dir = getClipboardFolder()
        dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to get cache size")
        0L
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "未知大小"

        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
