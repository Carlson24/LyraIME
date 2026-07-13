/*
 * SPDX-FileCopyrightText: 2026 LyraIME Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.osfans.trime.data.base.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CrashLogExporter {
    private const val LOG_DIR_NAME = "logs"
    private const val RETENTION_MS = 3L * 24 * 60 * 60 * 1000

    private val excludedTags = listOf("InsetsSource", "ViewRootImplStubImpl", "MIUIInput")

    private val fileTimestampFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
    }

    private fun getLogDir(): File {
        val dir = File(DataManager.defaultDataDir, LOG_DIR_NAME)
        dir.mkdirs()
        return dir
    }

    private fun collectLogcatSync(pid: Int): String = runCatching {
        subprocess("logcat", "--pid=$pid", "-v", "time", "-d")
            .inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
                    .filter { line -> excludedTags.none { line.contains(it) } }
                    .joinToString("\n")
            }
    }.getOrDefault("")

    private fun formatFile(dir: File, prefix: String, body: StringBuilder.() -> Unit): File {
        val timestamp = fileTimestampFormat.format(Date())
        val file = File(dir, "$prefix-$timestamp.txt")
        FileWriter(file).use { writer ->
            writer.append(buildString(body))
        }
        return file
    }

    fun exportCrash(context: Context, throwable: Throwable) {
        runCatching {
            val pid = Process.myPid()
            val file = formatFile(getLogDir(), "crash") {
                appendLine(DeviceInfo.get(context))
                appendLine()
                appendLine("--------- Crash stacktrace")
                appendLine("PID: $pid")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appendLine(sw.toString())
                appendLine()
                appendLine("--------- Logcat")
                append(collectLogcatSync(pid))
            }
            Timber.d("Crash log written to $file")
        }.onFailure {
            Timber.e(it, "Failed to write crash log")
        }
    }

    suspend fun exportDeployFailure(context: Context, deployTrace: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pid = Process.myPid()
                val file = formatFile(getLogDir(), "deploy") {
                    appendLine(DeviceInfo.get(context))
                    appendLine()
                    appendLine("--------- Deploy failure trace")
                    appendLine("PID: $pid")
                    appendLine(deployTrace)
                    appendLine()
                    appendLine("--------- Logcat")
                    append(collectLogcatSync(pid))
                }
                Timber.d("Deploy failure log written to $file")
            }.onFailure {
                Timber.e(it, "Failed to write deploy failure log")
            }
        }
    }

    suspend fun checkAndExportHistoricalExits(context: Context, previousPid: Int?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (previousPid == null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val reasons = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                for (reason in reasons) {
                    if (reason.pid != previousPid) continue
                    when (reason.reason) {
                        ApplicationExitInfo.REASON_ANR,
                        ApplicationExitInfo.REASON_CRASH_NATIVE,
                        -> {
                            exportAbnormalExit(context, previousPid, reason)
                        }
                    }
                    break
                }
            }.onFailure {
                Timber.e(it, "Failed to check historical exits")
            }
        }
    }

    private fun exportAbnormalExit(
        context: Context,
        previousPid: Int,
        reason: ApplicationExitInfo,
    ) {
        val reasonName = when (reason.reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            else -> "abnormal (reason=${reason.reason})"
        }
        val file = formatFile(getLogDir(), "abnormal") {
            appendLine(DeviceInfo.get(context))
            appendLine()
            appendLine("--------- $reasonName")
            appendLine("Previous PID: $previousPid")
            appendLine("Exit reason code: ${reason.reason}")
            reason.description?.let {
                appendLine("Description: $it")
            }
            appendLine()
            appendLine("--------- Logcat (previous PID)")
            append(collectLogcatSync(previousPid))
        }
        Timber.d("Abnormal exit log written to $file")
    }

    suspend fun cleanupOldLogs() {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = getLogDir()
                val cutoff = System.currentTimeMillis() - RETENTION_MS
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < cutoff) {
                        file.delete()
                        Timber.d("Deleted old log: ${file.name}")
                    }
                }
            }.onFailure {
                Timber.e(it, "Failed to cleanup old logs")
            }
        }
    }
}
