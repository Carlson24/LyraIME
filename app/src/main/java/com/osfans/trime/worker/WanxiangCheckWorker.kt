/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.R
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.compareVersions
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.util.readLocalWanxiangVersion
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WanxiangCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!sharedPref.getBoolean(AppPrefs.Wanxiang.AUTO_CHECK, false)) return Result.success()

        val localVersion = readLocalWanxiangVersion()
        if (localVersion == "v0.0.0") return Result.success()

        val remoteVersion = fetchRemoteVersion() ?: return Result.retry()
        if (compareVersions(remoteVersion, localVersion) > 0) {
            showNotification(remoteVersion, localVersion)
        }
        return Result.success()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun fetchRemoteVersion(): String? = try {
        val request = Request.Builder()
            .url(ResourceUrls.WANXIANG_API_LATEST_RELEASE)
            .header("User-Agent", ResourceUrls.USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val content = response.body?.string() ?: return null
                JSONObject(content).optString("tag_name", "").ifEmpty { null }
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
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
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.getBoolean(AppPrefs.Wanxiang.AUTO_CHECK, false)) return
            val intervalHours = prefs.getInt(AppPrefs.Wanxiang.CHECK_INTERVAL, 12)
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
