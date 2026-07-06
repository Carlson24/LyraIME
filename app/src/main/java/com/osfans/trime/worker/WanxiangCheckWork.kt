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
import java.util.concurrent.TimeUnit

class WanxiangCheckWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!sharedPref.getBoolean(AppPrefs.Wanxiang.AUTO_CHECK, false)) return Result.success()

        val downloadSource = sharedPref.getString(AppPrefs.Wanxiang.DOWNLOAD_SOURCE, "CNB") ?: "CNB"
        val token = if (downloadSource == "CNB") {
            sharedPref.getString(AppPrefs.Wanxiang.CNB_TOKEN, "") ?: ""
        } else {
            sharedPref.getString(AppPrefs.Wanxiang.GH_TOKEN, "") ?: ""
        }
        if (downloadSource == "CNB" && token.isEmpty()) return Result.success()

        val localVersion = readLocalWanxiangVersion()
        if (localVersion == "v0.0.0") return Result.success()

        val remoteVersion = fetchRemoteVersion(downloadSource, token) ?: return Result.retry()
        var versionUpdate = false
        if (compareVersions(remoteVersion, localVersion) > 0) {
            showVersionNotification(remoteVersion, localVersion)
            versionUpdate = true
        }

        var contentUpdate = false
        val lastModelSha = sharedPref.getString(AppPrefs.Wanxiang.LAST_MODEL_SHA256, "") ?: ""
        if (lastModelSha.isNotEmpty()) {
            val result = fetchRemoteModelSha256(downloadSource, token)
            if (result != null) {
                val (modelSha, updatedAt) = result
                sharedPref.edit()
                    .putBoolean(AppPrefs.Wanxiang.NEEDS_UPDATE_MODEL, modelSha != lastModelSha)
                    .putString(AppPrefs.Wanxiang.LAST_MODEL_UPDATED_AT, updatedAt)
                    .apply()
                if (modelSha != lastModelSha) contentUpdate = true
            }
        } else {
            sharedPref.edit().putBoolean(AppPrefs.Wanxiang.NEEDS_UPDATE_MODEL, false).apply()
        }

        val lastDictSha = sharedPref.getString(AppPrefs.Wanxiang.LAST_DICT_SHA256, "") ?: ""
        if (lastDictSha.isNotEmpty()) {
            val result = fetchRemoteDictSha256(sharedPref, downloadSource, token)
            if (result != null) {
                val (dictSha, updatedAt) = result
                sharedPref.edit()
                    .putBoolean(AppPrefs.Wanxiang.NEEDS_UPDATE_DICT, dictSha != lastDictSha)
                    .putString(AppPrefs.Wanxiang.LAST_DICT_UPDATED_AT, updatedAt)
                    .apply()
                if (dictSha != lastDictSha) contentUpdate = true
            }
        } else {
            sharedPref.edit().putBoolean(AppPrefs.Wanxiang.NEEDS_UPDATE_DICT, false).apply()
        }

        if (contentUpdate && !versionUpdate) {
            showContentNotification()
        }

        return Result.success()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun fetchRemoteVersion(source: String, token: String): String? {
        val json = ResourceUrls.fetchReleaseJson(ResourceUrls.schemaApi(source), token, client)
        return json?.optString("tag_name", "")?.ifEmpty { null }
    }

    private suspend fun fetchRemoteModelSha256(
        source: String,
        token: String,
    ): Pair<String, String>? {
        val json = ResourceUrls.fetchReleaseJson(ResourceUrls.modelApi(source), token, client) ?: return null
        val asset = ResourceUrls.findAssetByName(json, "wanxiang-lts-zh-hans.gram", source) ?: return null
        if (asset.sha256.isEmpty()) return null
        return Pair(asset.sha256, asset.releaseUpdatedAt)
    }

    private suspend fun fetchRemoteDictSha256(
        sharedPref: android.content.SharedPreferences,
        source: String,
        token: String,
    ): Pair<String, String>? {
        val isPro = sharedPref.getString(AppPrefs.Wanxiang.IS_PRO, "pro") ?: "pro"
        val auxScheme = sharedPref.getString(AppPrefs.Wanxiang.AUX_SCHEME, "zrm") ?: "zrm"
        val dictFile = when (isPro) {
            "pro" -> "pro-$auxScheme-fuzhu-dicts.zip"
            "pure" -> "pure-dicts.zip"
            else -> "base-dicts.zip"
        }
        val json = ResourceUrls.fetchReleaseJson(ResourceUrls.dictApi(source), token, client) ?: return null
        val asset = ResourceUrls.findAssetByName(json, dictFile, source) ?: return null
        if (asset.sha256.isEmpty()) return null
        return Pair(asset.sha256, asset.releaseUpdatedAt)
    }

    private fun showVersionNotification(remoteVersion: String, localVersion: String) {
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
        nm.notify(NOTIFICATION_ID_VERSION, builder.build())
    }

    private fun showContentNotification() {
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
            .setContentTitle(context.getString(R.string.wanxiang_content_update_title))
            .setContentText(context.getString(R.string.wanxiang_content_update_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_CONTENT, builder.build())
    }

    companion object {
        private const val WORK_KEY = "wanxiang_update_check"
        private const val NOTIFICATION_ID_VERSION = 4321
        private const val NOTIFICATION_ID_CONTENT = 4322

        fun start(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.getBoolean(AppPrefs.Wanxiang.AUTO_CHECK, false)) return
            val intervalHours = prefs.getInt(AppPrefs.Wanxiang.CHECK_INTERVAL, 12)
            val workManager = WorkManager.getInstance(context.applicationContext)
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WanxiangCheckWork>(
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
