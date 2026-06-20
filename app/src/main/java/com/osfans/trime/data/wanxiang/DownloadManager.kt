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
import com.osfans.trime.util.FileDownloader
import com.osfans.trime.util.extractTarBz2ToDir
import com.osfans.trime.util.extractTarGzToDir
import com.osfans.trime.util.extractTarZstToDir
import com.osfans.trime.util.extractZipToDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
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

        try {
            val downloadOnProgress: (Float) -> Unit = { progress ->
                task.progress = progress
                when {
                    task.url.startsWith("/") || task.url.startsWith("file://") || task.url.startsWith("content://") -> {
                        task.status = if (progress < 0f) {
                            context.getString(R.string.wanxiang_dl_reading_local)
                        } else {
                            context.getString(R.string.wanxiang_dl_local_done)
                        }
                    }
                }
                onProgress(task)
            }

            try {
                FileDownloader.download(
                    url = task.url,
                    destFile = tmpFile,
                    token = token,
                    expectedSha256 = task.expectedSha256,
                    contentResolver = context.contentResolver,
                    onProgress = downloadOnProgress,
                    isCancelled = isCancelled,
                )
            } catch (e: FileDownloader.Error) {
                task.isError = true
                task.progress = 0f
                task.status = context.getString(R.string.wanxiang_dl_fetch_failed, e.message ?: "")
                onProgress(task)
                return@withContext
            } catch (e: CancellationException) {
                task.isError = true
                task.progress = 0f
                task.status = context.getString(R.string.wanxiang_dl_network_error)
                onProgress(task)
                throw e
            }

            if (isCancelled()) throw CancellationException("Download cancelled")

            deploy(task, tmpFile, stagingDir, rules, isDict, targetPaths, context, onProgress)
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

            if (task.needsDecompress && task.url.endsWith(".zip")) {
                extractZipToDir(tmpFile.inputStream(), extractDir)
            } else if (task.needsDecompress && task.url.endsWith(".tar.gz")) {
                extractTarGzToDir(tmpFile.inputStream(), extractDir)
            } else if (task.needsDecompress && task.url.endsWith(".tar.bz2")) {
                extractTarBz2ToDir(tmpFile.inputStream(), extractDir)
            } else if (task.needsDecompress && task.url.endsWith(".tar.zst")) {
                extractTarZstToDir(tmpFile.inputStream(), extractDir)
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
