/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.wanxiang

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.computeFileSha256
import com.osfans.trime.util.extractZipToTempDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

object DownloadManager {

    suspend fun downloadAndDeploy(
        task: TaskState,
        token: String,
        context: Context,
        rules: List<String>,
        isDict: Boolean = false,
        targetPaths: List<String> = emptyList(),
        onProgress: (TaskState) -> Unit,
        isCancelled: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "wanxiang_staging")
        stagingDir.mkdirs()
        val tmpFile = File(stagingDir, "${task.url.substringAfterLast("/")}.tmp")
        var success = false
        var lastErrorMsg = ""

        val isLocalFile = task.url.startsWith("/") ||
            task.url.startsWith("file://") ||
            task.url.startsWith("content://")

        try {
            if (isLocalFile) {
                success = downloadLocal(task, tmpFile, context)
                if (!success) lastErrorMsg = context.getString(R.string.wanxiang_dl_local_error)
            } else {
                for (attempt in 1..3) {
                    if (isCancelled()) throw CancellationException("Download cancelled")
                    task.status = if (attempt > 1) {
                        context.getString(R.string.wanxiang_dl_retrying, attempt)
                    } else {
                        context.getString(R.string.wanxiang_dl_connecting)
                    }
                    onProgress(task)
                    try {
                        success = downloadRemote(task, tmpFile, token, context, onProgress, isCancelled)
                        if (success) break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastErrorMsg = e.message ?: context.getString(R.string.wanxiang_dl_network_error)
                        delay(1000)
                    }
                }
            }

            if (success) {
                if (isCancelled()) throw CancellationException("Download cancelled")
                if (!verifySha256(task, tmpFile, context, onProgress)) {
                    return@withContext
                }
                deploy(task, tmpFile, stagingDir, rules, isDict, targetPaths, context, onProgress)
            } else {
                task.isError = true
                task.progress = 0f
                task.status = context.getString(R.string.wanxiang_dl_fetch_failed, lastErrorMsg)
                onProgress(task)
            }
        } catch (e: CancellationException) {
            task.isError = true
            task.progress = 0f
            task.status = context.getString(R.string.wanxiang_dl_network_error)
            onProgress(task)
            throw e
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun verifySha256(
        task: TaskState,
        tmpFile: File,
        context: Context,
        onProgress: (TaskState) -> Unit,
    ): Boolean {
        val expected = task.expectedSha256 ?: return true
        val computed = computeFileSha256(tmpFile)
        if (computed != null && computed.equals(expected, ignoreCase = true)) return true
        task.isError = true
        task.isFinished = false
        task.progress = 0f
        task.status = context.getString(R.string.wanxiang_dl_fetch_failed, "SHA256 mismatch")
        onProgress(task)
        return false
    }

    private fun downloadLocal(
        task: TaskState,
        tmpFile: File,
        context: Context,
    ): Boolean {
        return try {
            task.status = context.getString(R.string.wanxiang_dl_reading_local)
            task.progress = -1f
            val uri = if (task.url.startsWith("/")) {
                File(task.url).toUri()
            } else {
                task.url.toUri()
            }
            val input = context.contentResolver.openInputStream(uri)
                ?: return false
            input.use { inputStream ->
                FileOutputStream(tmpFile).use { output -> inputStream.copyTo(output) }
            }
            task.progress = 1f
            task.status = context.getString(R.string.wanxiang_dl_local_done)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun downloadRemote(
        task: TaskState,
        tmpFile: File,
        token: String,
        context: Context,
        onProgress: (TaskState) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val finalUrlStr = resolveRedirects(task.url, token)

        val totalSize = queryContentLength(finalUrlStr, token)
        if (totalSize > 0) {
            try {
                downloadMultiThread(task, tmpFile, finalUrlStr, token, totalSize, context, onProgress, isCancelled)
                task.progress = 1f
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                task.status = context.getString(R.string.wanxiang_dl_multithread_failed)
                onProgress(task)
            }
        }
        try {
            downloadSingleThread(task, tmpFile, finalUrlStr, token, context, onProgress, isCancelled)
            task.progress = 1f
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun resolveRedirects(urlStr: String, token: String): String {
        var finalUrl = urlStr
        for (i in 0 until 5) {
            val conn = URL(finalUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            setHeaders(conn, token, finalUrl)
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 10000
            val code = conn.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    finalUrl = if (location.startsWith("http")) {
                        location
                    } else {
                        URL(URL(finalUrl), location).toString()
                    }
                    conn.disconnect()
                    continue
                }
            }
            conn.disconnect()
            break
        }
        return finalUrl
    }

    private fun queryContentLength(urlStr: String, token: String): Long {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        setHeaders(conn, token, urlStr)
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 10000
        val size = if (conn.responseCode == 200 || conn.responseCode == 206) {
            conn.contentLength.toLong()
        } else {
            0L
        }
        conn.disconnect()
        return size
    }

    private suspend fun downloadMultiThread(
        task: TaskState,
        tmpFile: File,
        urlStr: String,
        token: String,
        totalSize: Long,
        context: Context,
        onProgress: (TaskState) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val threadCount = 3
        val chunkSize = totalSize / threadCount
        val downloadedLen = AtomicLong(0)
        val lastUpdate = AtomicLong(System.currentTimeMillis())
        val stagingDir = tmpFile.parentFile!!

        coroutineScope {
            val jobs = (0 until threadCount).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunkSize
                    val end = if (i == threadCount - 1) totalSize - 1 else (start + chunkSize - 1)
                    val partConn = URL(urlStr).openConnection() as HttpURLConnection
                    try {
                        setHeaders(partConn, token, urlStr)
                        partConn.setRequestProperty("Range", "bytes=$start-$end")
                        partConn.connectTimeout = 10000
                        partConn.readTimeout = 10000
                        val partFile = File(stagingDir, "${tmpFile.name}.part$i")
                        partConn.inputStream.buffered().use { input ->
                            FileOutputStream(partFile).buffered().use { output ->
                                val buf = ByteArray(65536)
                                var count: Int
                                while (input.read(buf).also { count = it } != -1) {
                                    if (isCancelled()) {
                                        throw CancellationException("Download cancelled")
                                    }
                                    output.write(buf, 0, count)
                                    val current = downloadedLen.addAndGet(count.toLong())
                                    if (System.currentTimeMillis() - lastUpdate.get() > 150) {
                                        lastUpdate.set(System.currentTimeMillis())
                                        launch(Dispatchers.Main) {
                                            val currentMb = "%.1f".format(current / 1024.0 / 1024.0)
                                            val totalMb = "%.1f".format(totalSize / 1024.0 / 1024.0)
                                            task.progress = current.toFloat() / totalSize.toFloat()
                                            task.status = context.getString(
                                                R.string.wanxiang_dl_mb_multithread,
                                                "$currentMb / $totalMb",
                                            )
                                            onProgress(task)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        partConn.disconnect()
                        throw e
                    } finally {
                        partConn.disconnect()
                    }
                }
            }
            jobs.awaitAll()
        }
        if (isCancelled()) throw CancellationException("Download cancelled")
        task.status = context.getString(R.string.wanxiang_dl_assembling)
        onProgress(task)
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
        task: TaskState,
        tmpFile: File,
        urlStr: String,
        token: String,
        context: Context,
        onProgress: (TaskState) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            setHeaders(conn, token, urlStr)
            val fallbackSize = conn.contentLength.toLong()
            task.progress = if (fallbackSize > 0) 0f else -1f
            conn.inputStream.buffered().use { input ->
                FileOutputStream(tmpFile).buffered().use { output ->
                    val buf = ByteArray(131072)
                    var count: Int
                    var downloaded = 0L
                    var lastUpdate = System.currentTimeMillis()
                    while (input.read(buf).also { count = it } != -1) {
                        if (isCancelled()) {
                            throw CancellationException("Download cancelled")
                        }
                        downloaded += count
                        output.write(buf, 0, count)
                        if (System.currentTimeMillis() - lastUpdate > 150) {
                            lastUpdate = System.currentTimeMillis()
                            val currentMb = "%.1f".format(downloaded / 1024.0 / 1024.0)
                            task.progress = if (fallbackSize > 0) {
                                downloaded.toFloat() / fallbackSize
                            } else {
                                -1f
                            }
                            val sizeInfo = if (fallbackSize > 0) {
                                val totalMb = "%.1f".format(fallbackSize / 1024.0 / 1024.0)
                                "$currentMb / $totalMb"
                            } else {
                                currentMb
                            }
                            task.status = context.getString(R.string.wanxiang_dl_mb_single, sizeInfo)
                            onProgress(task)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            conn.disconnect()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun setHeaders(conn: HttpURLConnection, token: String, urlStr: String) {
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (urlStr.contains("github.com") && token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private suspend fun deploy(
        task: TaskState,
        tmpFile: File,
        stagingDir: File,
        rules: List<String>,
        isDict: Boolean,
        targetPaths: List<String>,
        context: Context,
        onProgress: (TaskState) -> Unit,
    ) {
        try {
            task.status = context.getString(R.string.wanxiang_dl_extracting)
            task.progress = -1f
            onProgress(task)

            val extractDir = File(stagingDir, "extracted_${System.currentTimeMillis()}")
            extractDir.mkdirs()

            if (task.url.endsWith(".zip")) {
                extractZipToTempDir(tmpFile.inputStream(), extractDir)
            } else {
                tmpFile.copyTo(File(extractDir, task.url.substringAfterLast("/")))
            }

            var realSrcDir = extractDir
            val subFiles = extractDir.listFiles()
            if (subFiles != null && subFiles.size == 1 && subFiles[0].isDirectory) {
                realSrcDir = subFiles[0]
            }

            val excludeRegexList = rules.mapNotNull { runCatching { Regex(it) }.getOrNull() }

            if (targetPaths.isEmpty()) {
                val target = if (isDict) {
                    File(DataManager.userDataDir, "dicts")
                } else {
                    DataManager.userDataDir
                }
                copyNormal(realSrcDir, target, excludeRegexList)
            } else {
                var successCount = 0
                val errorList = mutableListOf<String>()
                for ((index, pathStr) in targetPaths.withIndex()) {
                    try {
                        if (index > 0) delay(500)
                        if (pathStr == "DEFAULT") {
                            val target = if (isDict) {
                                File(DataManager.userDataDir, "dicts")
                            } else {
                                DataManager.userDataDir
                            }
                            copyNormal(realSrcDir, target, excludeRegexList)
                        } else {
                            val rootDoc = DocumentFile.fromTreeUri(context, pathStr.toUri())
                                ?: throw Exception(context.getString(R.string.wanxiang_dl_auth_expired))
                            var targetDoc = rootDoc
                            if (isDict) {
                                val dictsDoc = rootDoc.findFile("dicts")
                                    ?: rootDoc.createDirectory("dicts")
                                    ?: rootDoc.findFile("dicts")
                                    ?: throw Exception(context.getString(R.string.wanxiang_dl_saf_denied))
                                targetDoc = dictsDoc
                            }
                            copySaf(context, realSrcDir, targetDoc, excludeRegexList)
                        }
                        successCount++
                    } catch (e: Exception) {
                        val pathName = if (pathStr == "DEFAULT") {
                            context.getString(R.string.wanxiang_default)
                        } else {
                            context.getString(R.string.wanxiang_granted) + (index + 1)
                        }
                        errorList.add(context.getString(R.string.wanxiang_dl_failed_item, pathName, e.message ?: e.javaClass.simpleName))
                    }
                }
                if (errorList.isNotEmpty()) {
                    task.status = if (successCount == 0) {
                        context.getString(R.string.wanxiang_dl_all_failed) + " [${errorList.joinToString()}]"
                    } else {
                        context.getString(R.string.wanxiang_dl_partial) + " [${context.getString(R.string.wanxiang_dl_fetch_failed, errorList.joinToString())}]"
                    }
                }
            }

            task.isFinished = true
            task.progress = 1f
            task.status = context.getString(R.string.wanxiang_dl_done)
            onProgress(task)
        } catch (e: Exception) {
            task.isError = true
            task.progress = 0f
            task.status = context.getString(R.string.wanxiang_dl_extract_failed, e.message)
            onProgress(task)
        }
    }

    private fun copyNormal(src: File, dest: File, rules: List<Regex>, currentPath: String = "") {
        dest.mkdirs()
        src.listFiles()?.forEach { file ->
            val relPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
            val targetFile = File(dest, file.name)
            if (rules.any { it.containsMatchIn(relPath) }) return@forEach
            if (file.isDirectory) {
                copyNormal(file, targetFile, rules, relPath)
            } else {
                targetFile.delete()
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun copySaf(context: Context, src: File, dest: DocumentFile, rules: List<Regex>, currentPath: String = "") {
        src.listFiles()?.forEach { file ->
            val relPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
            if (rules.any { it.containsMatchIn(relPath) }) return@forEach
            if (file.isDirectory) {
                val nextDest = dest.findFile(file.name)
                    ?: dest.createDirectory(file.name)
                    ?: dest.findFile(file.name)
                    ?: throw Exception(context.getString(R.string.wanxiang_dl_dir_locked, file.name))
                copySaf(context, file, nextDest, rules, relPath)
            } else {
                dest.findFile(file.name)?.takeIf { it.exists() }?.delete()
                val newDoc = dest.createFile("*/*", file.name)
                    ?: dest.findFile(file.name)
                    ?: throw Exception(context.getString(R.string.wanxiang_dl_file_conflict, file.name))
                context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw Exception(context.getString(R.string.wanxiang_dl_file_stream_busy, file.name))
            }
        }
    }
}
