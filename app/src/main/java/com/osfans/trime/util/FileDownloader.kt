/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.ContentResolver
import androidx.core.net.toUri
import com.osfans.trime.data.ResourceUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

object FileDownloader {
    class Error(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun download(
        url: String,
        destFile: File,
        token: String = "",
        expectedSha256: String? = null,
        contentResolver: ContentResolver? = null,
        onProgress: ((Float) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ) {
        val isLocalFile = url.startsWith("/") ||
            url.startsWith("file://") ||
            url.startsWith("content://")

        var success = false
        var lastErrorMsg = ""

        if (isLocalFile) {
            success = downloadLocal(url, destFile, contentResolver)
            if (!success) lastErrorMsg = "Local file read failed"
        } else {
            for (attempt in 1..3) {
                if (isCancelled()) throw CancellationException("Download cancelled")
                try {
                    success = downloadRemote(url, destFile, token, onProgress, isCancelled)
                    if (success) break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Network error"
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        if (!success) {
            throw Error(lastErrorMsg.ifBlank { "Download failed" })
        }

        if (isCancelled()) throw CancellationException("Download cancelled")

        verifySha256(destFile, expectedSha256)
    }

    private fun verifySha256(file: File, expectedSha256: String?) {
        val expected = expectedSha256 ?: return
        val computed = computeFileSha256(file)
        if (computed == null || !computed.equals(expected, ignoreCase = true)) {
            throw Error("SHA256 mismatch")
        }
    }

    private fun buildRequest(urlStr: String, token: String): Request.Builder {
        val builder = Request.Builder().url(urlStr)
            .header("User-Agent", ResourceUrls.USER_AGENT)
        if (urlStr.contains("github.com") && token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    private fun downloadLocal(
        url: String,
        tmpFile: File,
        contentResolver: ContentResolver?,
    ): Boolean {
        return try {
            val uri = if (url.startsWith("/")) {
                File(url).toUri()
            } else {
                url.toUri()
            }
            val input = contentResolver?.openInputStream(uri) ?: return false
            input.use { inputStream ->
                FileOutputStream(tmpFile).use { output -> inputStream.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun downloadRemote(
        url: String,
        tmpFile: File,
        token: String,
        onProgress: ((Float) -> Unit)?,
        isCancelled: () -> Boolean,
    ): Boolean {
        val finalUrlStr = resolveRedirects(url, token)
        val totalSize = queryContentLength(finalUrlStr, token)
        if (totalSize > 0) {
            try {
                downloadMultiThread(tmpFile, finalUrlStr, token, totalSize, onProgress, isCancelled)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        try {
            downloadSingleThread(tmpFile, finalUrlStr, token, onProgress, isCancelled)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun resolveRedirects(urlStr: String, token: String): String = try {
        val request = buildRequest(urlStr, token).head().build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    } catch (_: Exception) {
        urlStr
    }

    private fun queryContentLength(urlStr: String, token: String): Long = try {
        val request = buildRequest(urlStr, token).head().build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.header("Content-Length")?.toLongOrNull() ?: -1L
            } else {
                0L
            }
        }
    } catch (_: Exception) {
        0L
    }

    private suspend fun downloadMultiThread(
        tmpFile: File,
        urlStr: String,
        token: String,
        totalSize: Long,
        onProgress: ((Float) -> Unit)?,
        isCancelled: () -> Boolean,
    ) {
        val threadCount = 3
        val chunkSize = totalSize / threadCount
        val downloadedLen = AtomicLong(0)
        val stagingDir = tmpFile.parentFile!!

        coroutineScope {
            val jobs = (0 until threadCount).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunkSize
                    val end = if (i == threadCount - 1) totalSize - 1 else (start + chunkSize - 1)
                    val request = buildRequest(urlStr, token)
                        .header("Range", "bytes=$start-$end")
                        .get()
                        .build()
                    try {
                        client.newCall(request).execute().use { response ->
                            val partFile = File(stagingDir, "${tmpFile.name}.part$i")
                            response.body.byteStream().buffered().use { input ->
                                FileOutputStream(partFile).buffered().use { output ->
                                    val buf = ByteArray(65536)
                                    var count: Int
                                    while (input.read(buf).also { count = it } != -1) {
                                        if (isCancelled()) {
                                            throw CancellationException("Download cancelled")
                                        }
                                        output.write(buf, 0, count)
                                        downloadedLen.addAndGet(count.toLong())
                                        onProgress?.invoke(downloadedLen.get().toFloat() / totalSize.toFloat())
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    }
                }
            }
            jobs.awaitAll()
        }
        if (isCancelled()) throw CancellationException("Download cancelled")
        withContext(Dispatchers.IO) {
            FileOutputStream(tmpFile).buffered()
        }.use { output ->
            for (i in 0 until threadCount) {
                val partFile = File(stagingDir, "${tmpFile.name}.part$i")
                partFile.inputStream().buffered().use { it.copyTo(output) }
                partFile.delete()
            }
        }
    }

    private fun downloadSingleThread(
        tmpFile: File,
        urlStr: String,
        token: String,
        onProgress: ((Float) -> Unit)?,
        isCancelled: () -> Boolean,
    ) {
        val request = buildRequest(urlStr, token).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            val fallbackSize = body.contentLength()
            body.byteStream().buffered().use { input ->
                FileOutputStream(tmpFile).buffered().use { output ->
                    val buf = ByteArray(131072)
                    var count: Int
                    var downloaded = 0L
                    while (input.read(buf).also { count = it } != -1) {
                        if (isCancelled()) {
                            throw CancellationException("Download cancelled")
                        }
                        downloaded += count
                        output.write(buf, 0, count)
                        if (fallbackSize > 0) {
                            onProgress?.invoke(downloaded.toFloat() / fallbackSize)
                        } else {
                            onProgress?.invoke(-1f)
                        }
                    }
                }
            }
        }
    }
}
