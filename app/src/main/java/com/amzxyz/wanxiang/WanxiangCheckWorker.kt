/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.amzxyz.wanxiang

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.createNotificationChannel
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class WanxiangCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("WanxiangPrefs", 0)
        if (!sharedPref.getBoolean("auto_check", false)) return Result.success()

        val localVersion = readLocalVersion()
        if (localVersion == "v0.0.0") return Result.success()

        val remoteVersion = fetchRemoteVersion() ?: return Result.retry()
        if (compareVersions(remoteVersion, localVersion) > 0) {
            showNotification(remoteVersion, localVersion)
        }
        return Result.success()
    }

    private fun readLocalVersion(): String = try {
        val userDataFile = File(DataManager.userDataDir, "lua/wanxiang/wanxiang.lua")
        val sharedDataFile = File(DataManager.sharedDataDir, "wanxiang/wanxiang.lua")
        val file = when {
            userDataFile.exists() -> userDataFile
            sharedDataFile.exists() -> sharedDataFile
            else -> null
        }
        if (file != null) {
            val content = file.readText()
            Regex("""wanxiang\.version\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1) ?: "v0.0.0"
        } else {
            "v0.0.0"
        }
    } catch (_: Exception) {
        "v0.0.0"
    }

    private fun fetchRemoteVersion(): String? = try {
        val url = URL("https://api.github.com/repos/amzxyz/rime-wanxiang/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "WanxiangUpdater-Agent")
        conn.connectTimeout = 10000
        conn.connect()
        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().readText()
            Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                .find(content)?.groupValues?.get(1)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun compareVersions(a: String, b: String): Int {
        fun parse(v: String): List<Int> = v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val va = parse(a)
        val vb = parse(b)
        for (i in 0 until maxOf(va.size, vb.size)) {
            val aa = va.getOrElse(i) { 0 }
            val bb = vb.getOrElse(i) { 0 }
            if (aa != bb) return aa.compareTo(bb)
        }
        return 0
    }

    private fun showNotification(remoteVersion: String, localVersion: String) {
        val context = applicationContext
        val channelId = "wanxiang-update-check"
        createNotificationChannel(channelId, context.getString(R.string.wanxiang_updater))

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.Wanxiang)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(context.getString(R.string.wanxiang_new_version_title))
            .setContentText(context.getString(R.string.wanxiang_new_version_text, remoteVersion, localVersion))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val WORK_KEY = "wanxiang_update_check"
        private const val NOTIFICATION_ID = 4321

        fun start(context: Context) {
            val prefs = context.getSharedPreferences("WanxiangPrefs", 0)
            if (!prefs.getBoolean("auto_check", false)) return
            val intervalHours = prefs.getInt("check_interval", 12)
            val workManager = WorkManager.getInstance(context.applicationContext)
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WanxiangCheckWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS,
                15,
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_KEY,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_KEY)
        }
    }
}
