/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.backup.BackupManager
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
            return doBackgroundSync()
        } catch (e: Exception) {
            Timber.e(e, "Background sync job failed.")
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

        suspend fun backupSettingsToSyncDir() {
            try {
                val userId = readInstallationId()
                val syncDir = File(DataManager.userDataDir, "sync").resolve(userId)
                syncDir.mkdirs()
                val backupFile = File(syncDir, SETTINGS_BACKUP_FILENAME)
                val backupData = BackupManager.createBackup(
                    includePreferences = true,
                    includeClipboard = false,
                    includeCollection = true,
                    includeWanxiang = true,
                    includeCustomTasks = true,
                )
                BackupManager.saveBackupToFile(backupData, backupFile)
                Timber.d("Settings backup saved to sync dir: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to backup settings to sync dir")
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
            internalStart(context, ExistingPeriodicWorkPolicy.UPDATE)
        }

        fun forceStart(context: Context) {
            internalStart(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        private fun internalStart(
            context: Context,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val instance = WorkManager.getInstance(context.applicationContext)
            if (!enable) {
                instance.cancelUniqueWork(PERIODIC_BACKGROUND_SYNC_KEY)
                Timber.i("BackgroundSyncWork canceled!")
                return
            }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()

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
                PeriodicWorkRequestBuilder<BackgroundSyncWork>(
                    24,
                    TimeUnit.HOURS,
                    15,
                    TimeUnit.MINUTES,
                ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
            instance.enqueueUniquePeriodicWork(
                PERIODIC_BACKGROUND_SYNC_KEY,
                policy,
                workRequest,
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
