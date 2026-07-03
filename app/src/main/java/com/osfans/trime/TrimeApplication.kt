/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.data.clipboard.ClipboardHistoryStore
import com.osfans.trime.data.clipboard.SyncClipboardManager
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.SyncEntryType
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.clipboard.ScreenshotClipboardWatcher
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.util.isNightMode
import com.osfans.trime.util.toast
import com.osfans.trime.worker.BackgroundSyncWork
import com.osfans.trime.worker.WanxiangCheckWork
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import splitties.systemservices.clipboardManager
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    val coroutineScope = MainScope() + CoroutineName("TrimeApplication")

    private val rimeIntentReceiver = RimeIntentReceiver()

    private fun registerBroadcastReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            PERMISSION_TEST_INPUT_METHOD,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                val crashTime = System.currentTimeMillis()
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val lastCrashTimePrefKey = "last_crash_time"
                val lastCrashTime = sharedPrefs.getLong(lastCrashTimePrefKey, -1L)
                sharedPrefs.edit(commit = true) {
                    putLong(lastCrashTimePrefKey, crashTime)
                }
                if (crashTime - lastCrashTime <= 10_000L) {
                    // continuous crashes within 10 seconds, maybe in a crash loop. just bail
                    exitProcess(10)
                }
                startActivity(
                    Intent(applicationContext, LogActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(LogActivity.FROM_CRASH, true)
                        // avoid transaction overflow
                        val truncated =
                            e.stackTraceToString().let {
                                if (it.length > MAX_STACKTRACE_SIZE) {
                                    it.take(MAX_STACKTRACE_SIZE) + "<truncated>"
                                } else {
                                    it
                                }
                            }
                        putExtra(LogActivity.CRASH_STACK_TRACE, truncated)
                    },
                )
                exitProcess(10)
            }
        }
        instance = this
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(
                    object : Timber.DebugTree() {
                        override fun createStackElementTag(element: StackTraceElement): String = "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            super.log(
                                priority,
                                "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                                "${tag?.substringAfter('|')}] $message",
                                t,
                            )
                        }
                    },
                )
            } else {
                Timber.plant(
                    object : Timber.Tree() {
                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            if (priority < Log.INFO) return
                            Log.println(priority, "[${Thread.currentThread().name}]", message)
                        }
                    },
                )
            }
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val appPrefs = AppPrefs.initDefault(sharedPreferences)
            // record last pid for crash logs
            appPrefs.internal.pid.apply {
                val currentPid = Process.myPid()
                lastPid = getValue()
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
                setValue(currentPid)
            }
            ClipboardHelper.init(applicationContext)
            initScreenshotWatcher(sharedPreferences)
            initSyncClipboardManager(sharedPreferences)
            registerBroadcastReceiver()
            startWorkManager()
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            ColorManager.onSystemNightModeChange(newConfig.isNightMode())
        } catch (e: Exception) {
            Timber.w(e, "Something wrong on configuration changed")
        }
    }

    private fun startWorkManager() {
        coroutineScope.launch {
            BackgroundSyncWork.start(applicationContext)
            WanxiangCheckWork.start(applicationContext)
        }
    }

    private fun initSyncClipboardManager(sharedPreferences: SharedPreferences) {
        val appPrefs = AppPrefs.defaultInstance()
        val clipboardPrefs = appPrefs.clipboard
        val historyStore = ClipboardHistoryStore(ClipboardHelper.clipboardSyncDao)

        val mainHandler = Handler(Looper.getMainLooper())

        val manager = SyncClipboardManager(
            context = applicationContext,
            prefs = clipboardPrefs,
            scope = coroutineScope,
            listener = object : SyncClipboardManager.Listener {
                override fun onPulledNewContent(text: String) {
                    mainHandler.post {
                        applicationContext.toast(R.string.sync_clipboard_pulled_toast)
                        coroutineScope.launch {
                            ClipboardHelper.importRemoteEntry(text)
                        }
                    }
                }

                override fun onUploadSuccess() {}

                override fun onUploadFailed(reason: String?) {
                    mainHandler.post {
                        val msg = resources.getString(
                            R.string.sync_clipboard_upload_failed_toast,
                            reason ?: "unknown",
                        )
                        applicationContext.toast(msg)
                    }
                }

                override fun onFilePulled(type: SyncEntryType, fileName: String, serverFileName: String) {
                    mainHandler.post {
                        val msg = resources.getString(
                            R.string.sync_clipboard_file_received_toast,
                            fileName,
                        )
                        applicationContext.toast(msg)
                    }
                }
            },
            clipboardStore = historyStore,
        )
        syncClipboardManager = manager

        if (clipboardPrefs.syncClipboardEnabled) {
            manager.start()
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@registerOnSharedPreferenceChangeListener
            if (key == AppPrefs.Clipboard.SYNC_CLIPBOARD_ENABLED ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_SERVER_BASE ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_USERNAME ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_PASSWORD ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_AUTO_PULL ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_PULL_INTERVAL_SEC ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_SERVER_TYPE ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_S3_REGION ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_S3_BUCKET ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_S3_OBJECT_PREFIX ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_S3_FORCE_PATH_STYLE ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_SIGNALR_ENABLED ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_AUTO_DOWNLOAD_MAX_SIZE ||
                key == AppPrefs.Clipboard.SYNC_CLIPBOARD_WEBDAV_REMOTE_PATH
            ) {
                Timber.d("Sync clipboard pref changed: $key")
                manager.onPrefsChanged()
            }
        }
    }

    private var screenshotWatcher: ScreenshotClipboardWatcher? = null

    private fun initScreenshotWatcher(sharedPreferences: SharedPreferences) {
        val appPrefs = AppPrefs.defaultInstance()
        val screenshotWatchPref = appPrefs.clipboard.clipboardScreenshotWatch
        Timber.d("Screenshot watcher init: preference value = ${screenshotWatchPref.getValue()}")
        if (screenshotWatchPref.getValue()) {
            startScreenshotWatcher()
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == AppPrefs.Clipboard.CLIPBOARD_SCREENSHOT_WATCH) {
                Timber.d("Screenshot watch pref changed: ${screenshotWatchPref.getValue()}")
                if (screenshotWatchPref.getValue()) {
                    startScreenshotWatcher()
                } else {
                    stopScreenshotWatcher()
                }
            }
        }
    }

    private fun startScreenshotWatcher() {
        if (screenshotWatcher == null) {
            screenshotWatcher = ScreenshotClipboardWatcher(
                this,
                clipboardManager,
            )
        }
        screenshotWatcher?.start()
    }

    private fun stopScreenshotWatcher() {
        screenshotWatcher?.stop()
        screenshotWatcher = null
    }

    companion object {
        private var instance: TrimeApplication? = null
        private var lastPid: Int? = null

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid

        private var syncClipboardManager: SyncClipboardManager? = null

        fun getSyncClipboardManager() = syncClipboardManager

        private const val MAX_STACKTRACE_SIZE = 128000

        /**
         * This permission is requested by com.android.shell, makes it possible to start
         * deploy from `adb shell am` command:
         * ```sh
         * adb shell am broadcast -a com.osfans.trime.action.DEPLOY
         * ```
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-7.0.0_r1/packages/Shell/AndroidManifest.xml#67
         *
         * other candidate: android.permission.TEST_INPUT_METHOD requires Android 14
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/packages/Shell/AndroidManifest.xml#628
         */
        const val PERMISSION_TEST_INPUT_METHOD = "android.permission.READ_INPUT_STATE"
    }
}
