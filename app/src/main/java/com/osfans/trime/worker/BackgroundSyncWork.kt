/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.backup.BackupManager
import com.osfans.trime.data.backup.WebDavSync
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import timber.log.Timber
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BackgroundSyncWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Timber.i("Starting background sync ...")
            val result = doBackgroundSync()
            scheduleNext(applicationContext)
            return result
        } catch (e: Exception) {
            Timber.e(e, "Background sync job failed.")
            scheduleNext(applicationContext)
            return Result.retry()
        }
    }

    private suspend fun doBackgroundSync(): Result {
        if (!enable) return Result.failure()

        backupSettingsToSyncDir()

        val rime = RimeDaemon.createSession(javaClass.name)
        val success = rime.runOnReady { syncUserData() }
        lastSyncTime = System.currentTimeMillis()
        lastSyncStatus = success
        RimeDaemon.destroySession(javaClass.name)

        syncWebDavIfEnabled()

        return if (success) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_BACKGROUND_SYNC_KEY = "periodic_background_sync"
        const val SETTINGS_BACKUP_FILENAME = "trime_settings_backup.json"

        private val prefs = AppPrefs.defaultInstance().profile
        private val enable by prefs.periodicBackgroundSync
        private val syncTime by prefs.periodicBackgroundSyncTime
        private var lastSyncStatus by prefs.lastBackgroundSyncStatus
        private var lastSyncTime by prefs.lastBackgroundSyncTime
        private var lastSyncSettingsHash by prefs.lastSyncSettingsHash
        private val webdavEnabled by prefs.webdavEnabled

        suspend fun backupSettingsToSyncDir() {
            try {
                val currentHash = BackupManager.computeSettingsFingerprint()
                if (currentHash == lastSyncSettingsHash && lastSyncSettingsHash.isNotEmpty()) {
                    Timber.d("Settings unchanged, skipping backup")
                    return
                }

                val userId = readInstallationId()
                val syncDir = File(DataManager.userDataDir, "sync").resolve(userId)
                syncDir.mkdirs()
                val backupFile = File(syncDir, SETTINGS_BACKUP_FILENAME)
                val backupData = BackupManager.createBackup(
                    includePreferences = true,
                    includeClipboard = true,
                    includeWanxiang = true,
                    includeCustomTasks = true,
                )
                BackupManager.saveBackupToFile(backupData, backupFile)
                lastSyncSettingsHash = currentHash
                Timber.d("Settings backup saved to sync dir: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to backup settings to sync dir")
            }
        }

        suspend fun syncWebDavIfEnabled() {
            if (!webdavEnabled) return
            if (prefs.webdavUrl.getValue().isEmpty()) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                WebDavSync.pushToServer()
                    .onFailure { Timber.w(it, "WebDAV sync failed") }
            }
        }

        private fun readInstallationId(): String {
            val file = File(DataManager.userDataDir, "installation.yaml")
            if (file.exists()) {
                val content = file.readText()
                Regex("""installation_id:\s*["']?([^"'\n\r]+)""").find(content)
                    ?.groupValues?.get(1)?.trim()?.let { return it }
            }
            return "unknown"
        }

        fun start(context: Context) {
            Timber.i("BackgroundSyncWork scheduled!")
            scheduleNext(context)
        }

        fun scheduleNext(context: Context) {
            if (!enable) {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(PERIODIC_BACKGROUND_SYNC_KEY)
                Timber.i("BackgroundSyncWork canceled!")
                return
            }

            val (hour, minute) = parseSyncTime(syncTime)
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val workRequest =
                OneTimeWorkRequestBuilder<BackgroundSyncWork>()
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    PERIODIC_BACKGROUND_SYNC_KEY,
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )

            Timber.i(
                "BackgroundSyncWork scheduled at %02d:%02d (delay: %d min)",
                hour,
                minute,
                TimeUnit.MILLISECONDS.toMinutes(initialDelay),
            )
        }

        private fun parseSyncTime(time: String): Pair<Int, Int> {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 2
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return hour to minute
        }
    }
}
