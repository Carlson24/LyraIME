/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.main.settings

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.wanxiang.DownloadManager
import com.osfans.trime.data.wanxiang.TaskState
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.compareVersions
import com.osfans.trime.util.computeFileSha256
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.util.readLocalWanxiangVersion
import com.osfans.trime.worker.WanxiangCheckWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class WanxiangUpdateSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().wanxiang) {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.defaultInstance().wanxiang

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var isDownloading = false
    private var downloadJob: Job? = null
    private var latestStableTag = "v1.0.0"
    private var currentLocalVersion = "v0.0.0"

    private var checkSchema = false
    private var checkDict = true
    private var checkModel = false

    private lateinit var versionDisplayPref: Preference

    private val onWorkerChange = PreferenceDelegateProvider.OnChangeListener { key ->
        when (key) {
            AppPrefs.Wanxiang.AUTO_CHECK -> {
                if (prefs.autoCheck.getValue()) {
                    WanxiangCheckWork.start(requireContext())
                } else {
                    WanxiangCheckWork.cancel(requireContext())
                }
            }
            AppPrefs.Wanxiang.CHECK_INTERVAL -> {
                WanxiangCheckWork.start(requireContext())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.registerOnChangeListener(onWorkerChange)
    }

    override fun onStart() {
        super.onStart()
        viewModel.enableToolbarEditButton(
            icon = R.drawable.ic_baseline_refresh_reversed_24,
        ) {
            viewModel.rime.launchOnReady { it.deploy() }
        }
        viewModel.enableToolbarDeleteButton(
            icon = R.drawable.ic_baseline_download_24,
        ) {
            if (!isDownloading) executeSelected()
        }
    }

    override fun onStop() {
        viewModel.disableToolbarEditButton()
        viewModel.disableToolbarDeleteButton()
        super.onStop()
    }

    override fun onDestroy() {
        prefs.unregisterOnChangeListener(onWorkerChange)
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        evaluateVisibility()
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            isIconSpaceReserved = false

            versionDisplayPref = Preference(ctx).apply {
                key = "wanxiang_version_display"
                title = getString(R.string.wanxiang_scheme_version)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    refreshLocalVersion()
                    fetchLatestVersionTag(notifyOnNew = true)
                    true
                }
            }
            addPreference(versionDisplayPref)

            prefs.createUi(this)

            addCategory(R.string.wanxiang_actions) {
                isIconSpaceReserved = false

                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = "wanxiang_action_schema"
                        title = getString(R.string.wanxiang_schema_only)
                        isChecked = checkSchema
                        isPersistent = false
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        setOnPreferenceChangeListener { _, newValue ->
                            checkSchema = newValue as Boolean
                            true
                        }
                    },
                )
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = "wanxiang_action_dict"
                        title = getString(R.string.wanxiang_dict_only)
                        isChecked = checkDict
                        isPersistent = false
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        setOnPreferenceChangeListener { _, newValue ->
                            checkDict = newValue as Boolean
                            true
                        }
                    },
                )
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = "wanxiang_action_model"
                        title = getString(R.string.wanxiang_model_only)
                        isChecked = checkModel
                        isPersistent = false
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        setOnPreferenceChangeListener { _, newValue ->
                            checkModel = newValue as Boolean
                            true
                        }
                    },
                )
            }
        }

        refreshLocalVersion()
        fetchLatestVersionTag()
    }

    private fun refreshLocalVersion() {
        currentLocalVersion = readLocalWanxiangVersion()
        updateVersionDisplay()
    }

    private fun updateVersionDisplay() {
        val channel = prefs.updateChannel.getValue()
        val isPro = prefs.isPro.getValue()
        val channelLabel = if (channel == "Stable") {
            getString(R.string.wanxiang_stable)
        } else {
            getString(R.string.wanxiang_preview)
        }
        val variant = when (isPro) {
            "pro" -> "Pro"
            "pure" -> "Pure"
            else -> "Base"
        }
        versionDisplayPref.summary = "$channelLabel ($variant) ($currentLocalVersion → $latestStableTag)"
    }

    private fun fetchLatestVersionTag(notifyOnNew: Boolean = false) {
        lifecycleScope.launch {
            var rateLimited = false
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(ResourceUrls.WANXIANG_API_LATEST_RELEASE)
                        .header("User-Agent", ResourceUrls.USER_AGENT)
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val content = response.body.string()
                            val tag = JSONObject(content).optString("tag_name", "")
                            if (tag.isNotEmpty()) latestStableTag = tag
                        } else if (response.code in setOf(403, 429)) {
                            val body = response.body.string()
                            if (body.contains("rate limit", ignoreCase = true) || body.contains("API rate limit", ignoreCase = true)) {
                                rateLimited = true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                updateVersionDisplay()
                val isNewer = compareVersions(latestStableTag, currentLocalVersion) > 0
                if (notifyOnNew && isNewer) {
                    showUpdateNotification()
                }
                if (rateLimited) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.wanxiang_rate_limit),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showUpdateNotification() {
        val context = requireContext()
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
            .setContentText(context.getString(R.string.wanxiang_new_version_text, latestStableTag, currentLocalVersion))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(4321, builder.build())
    }

    private fun executeSelected() {
        if (!checkSchema && !checkDict && !checkModel) return
        if (isDownloading) return

        val names = mutableListOf<String>()
        if (checkSchema) names.add(getString(R.string.wanxiang_schema_only))
        if (checkDict) names.add(getString(R.string.wanxiang_dict_only))
        if (checkModel) names.add(getString(R.string.wanxiang_model_only))
        val name = names.joinToString(" ")

        val dirPath = DataManager.userDataDir.absolutePath.removePrefix("/storage/emulated/0/")
        requireContext().buildDialog()
            .setTitle(getString(R.string.wanxiang_will_update, name))
            .setMessage(getString(R.string.wanxiang_confirm_execute, dirPath))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch { doExecuteSelected() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun doExecuteSelected() {
        val isPro = prefs.isPro.getValue()
        val auxScheme = prefs.auxScheme.getValue()
        val downloadSource = prefs.downloadSource.getValue()
        val updateChannel = prefs.updateChannel.getValue()

        val schemeStr = when (isPro) {
            "pro" -> auxScheme
            "pure" -> "pure"
            else -> "base"
        }
        val activeTag = if (downloadSource == "CNB") {
            if (updateChannel == "Stable") latestStableTag else "v1.0.0"
        } else {
            if (updateChannel == "Stable") latestStableTag else "dict-nightly"
        }
        val baseUrl = if (downloadSource == "CNB") {
            ResourceUrls.WANXIANG_CNB_RELEASES_BASE
        } else {
            ResourceUrls.WANXIANG_GITHUB_RELEASES_BASE
        }
        val dictBaseUrl = if (downloadSource == "CNB") {
            ResourceUrls.WANXIANG_CNB_DICTS_BASE
        } else {
            ResourceUrls.WANXIANG_GITHUB_DICTS_BASE
        }
        val modelUrl = if (downloadSource == "CNB") {
            ResourceUrls.WANXIANG_CNB_MODEL
        } else {
            ResourceUrls.WANXIANG_GITHUB_MODEL
        }

        val urls = mutableListOf<String>()
        if (checkSchema) {
            urls.add("$baseUrl/$activeTag/rime-wanxiang-$schemeStr${if (isPro == "pro") "-fuzhu" else ""}.zip")
        }
        if (checkDict) {
            val dictPrefix = when (isPro) {
                "pro" -> "pro-$schemeStr-fuzhu"
                "pure" -> "pure"
                else -> "base"
            }
            urls.add("$dictBaseUrl/$dictPrefix-dicts.zip")
        }
        if (checkModel) {
            val result = withContext(Dispatchers.IO) { checkModelUpdate() }
            when (result) {
                ModelCheckResult.FETCH_FAILED -> {
                    val proceed = suspendCancellableCoroutine { cont ->
                        requireContext().buildDialog()
                            .setMessage(R.string.wanxiang_model_check_failed)
                            .setPositiveButton(R.string.wanxiang_continue_anyway) { _, _ -> cont.resume(true) }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> cont.resume(false) }
                            .setOnCancelListener { cont.resume(false) }
                            .show()
                    }
                    if (proceed) urls.add(modelUrl) else return
                }
                ModelCheckResult.UP_TO_DATE -> {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            R.string.wanxiang_model_up_to_date,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                ModelCheckResult.NEEDS_UPDATE -> urls.add(modelUrl)
            }
        }

        if (urls.isEmpty()) return
        val githubToken = prefs.ghToken.getValue()
        val rules = prefs.excludeRules.getValue().lines().filter { it.isNotBlank() }

        val expectedShas = if (downloadSource == "GitHub") {
            buildExpectedShaMap(schemeStr, isPro, updateChannel)
        } else {
            emptyMap()
        }

        startDownload(urls, rules, githubToken, expectedShas)
    }

    private enum class ModelCheckResult { FETCH_FAILED, UP_TO_DATE, NEEDS_UPDATE }

    private fun buildExpectedShaMap(
        schemeStr: String,
        isPro: String,
        updateChannel: String,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val schemaTag = if (updateChannel == "Stable") latestStableTag else "dict-nightly"
        val schemaFile = when (isPro) {
            "pro" -> "rime-wanxiang-$schemeStr-fuzhu.zip"
            else -> "rime-wanxiang-$schemeStr.zip"
        }
        val schemaAssets = fetchReleaseAssetsSha256(schemaTag)
        schemaAssets[schemaFile]?.let { result[schemaFile] = it }
        val dictFile = when (isPro) {
            "pro" -> "pro-$schemeStr-fuzhu-dicts.zip"
            "pure" -> "pure-dicts.zip"
            else -> "base-dicts.zip"
        }
        val dictAssets = fetchReleaseAssetsSha256("dict-nightly")
        dictAssets[dictFile]?.let { result[dictFile] = it }
        return result
    }

    private fun checkModelUpdate(): ModelCheckResult {
        val remoteSha = fetchRemoteModelSha256() ?: return ModelCheckResult.FETCH_FAILED
        val localFile = File(DataManager.userDataDir, "wanxiang-lts-zh-hans.gram")
        if (!localFile.exists()) return ModelCheckResult.NEEDS_UPDATE
        val localSha = computeFileSha256(localFile) ?: return ModelCheckResult.NEEDS_UPDATE
        return if (localSha == remoteSha) ModelCheckResult.UP_TO_DATE else ModelCheckResult.NEEDS_UPDATE
    }

    private fun fetchRemoteModelSha256(): String? {
        return try {
            val request = Request.Builder()
                .url(ResourceUrls.WANXIANG_API_RIME_LMDG_TAGS_LTS)
                .header("User-Agent", ResourceUrls.USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val content = response.body.string()
                val json = JSONObject(content)
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name") == "wanxiang-lts-zh-hans.gram") {
                        val digest = asset.optString("digest", "")
                        return digest.removePrefix("sha256:")
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchReleaseAssetsSha256(tag: String): Map<String, String> {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/amzxyz/rime-wanxiang/releases/tags/$tag")
                .header("User-Agent", ResourceUrls.USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyMap()
                val content = response.body.string()
                val json = JSONObject(content)
                val assets = json.getJSONArray("assets")
                val result = mutableMapOf<String, String>()
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "") ?: ""
                    val digest = asset.optString("digest", "")
                    if (name.isNotEmpty() && digest.isNotEmpty()) {
                        result[name] = digest.removePrefix("sha256:")
                    }
                }
                result
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun startDownload(
        urls: List<String>,
        rules: List<String>,
        githubToken: String,
        expectedShas: Map<String, String> = emptyMap(),
    ) {
        if (isDownloading) return
        isDownloading = true
        viewModel.disableToolbarDeleteButton()

        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val taskContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        }
        val scrollView = ScrollView(ctx).apply {
            addView(
                taskContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val tasks = urls.map { url ->
            val fName = url.substringAfterLast("/")
            val title = when {
                fName.contains("dicts") -> "${getString(R.string.wanxiang_package_dicts)} ($fName)"
                fName.contains("gram") -> "${getString(R.string.wanxiang_package_model)} ($fName)"
                else -> "${getString(R.string.wanxiang_package_schema)} ($fName)"
            }
            TaskState(title, url, expectedSha256 = expectedShas[fName])
        }

        val progressViews = mutableListOf<TextView>()
        val progressBars = mutableListOf<ProgressBar>()

        for (task in tasks) {
            val tv = TextView(ctx).apply {
                text = task.title
                setPadding(0, 8.dp(), 0, 4.dp())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            taskContainer.addView(tv)
            progressViews.add(tv)

            val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
            }
            taskContainer.addView(bar)
            progressBars.add(bar)
        }

        val dialog = ctx.buildDialog(R.string.wanxiang_task_progress)
            .setView(scrollView)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                downloadJob?.cancel()
            }
            .create()

        dialog.setOnDismissListener {
            isDownloading = false
            viewModel.enableToolbarDeleteButton(
                icon = R.drawable.ic_baseline_download_24,
            ) {
                if (!isDownloading) executeSelected()
            }
        }

        dialog.show()

        downloadJob = lifecycleScope.launch {
            var completed = false
            val allSucceeded: Boolean
            try {
                for ((i, task) in tasks.withIndex()) {
                    DownloadManager.downloadAndDeploy(
                        task = task,
                        token = githubToken,
                        context = ctx,
                        rules = rules,
                        isDict = task.url.contains("dicts"),
                        onProgress = { t ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                val status = when {
                                    t.isFinished || t.isError -> t.status
                                    t.progress >= 0f && t.totalBytes > 0 ->
                                        "${(t.progress * 100).toInt()}% - ${formatBytes(t.downloadedBytes)}/${formatBytes(t.totalBytes)}"
                                    t.progress >= 0f -> "${(t.progress * 100).toInt()}%"
                                    else -> t.status.ifEmpty { "…" }
                                }
                                progressViews[i].text = getString(
                                    R.string.wanxiang_dl_progress_line,
                                    task.title,
                                    status,
                                )
                                if (t.progress < 0) {
                                    progressBars[i].isIndeterminate = true
                                } else {
                                    progressBars[i].isIndeterminate = false
                                    progressBars[i].progress = (t.progress * 100).toInt().coerceIn(0, 100)
                                }
                            }
                        },
                        isCancelled = { downloadJob?.isActive != true },
                    )
                    if (task.isError) break
                }
                allSucceeded = tasks.none { it.isError }
                completed = true
                withContext(Dispatchers.Main) {
                    val button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    if (button != null) {
                        button.text = getString(android.R.string.ok)
                        button.setOnClickListener {
                            if (allSucceeded) {
                                lifecycleScope.launch {
                                    val session = RimeDaemon.createSession("wanxiang_deploy")
                                    try {
                                        session.runOnReady { deploy() }
                                    } catch (_: Exception) {
                                    } finally {
                                        RimeDaemon.destroySession("wanxiang_deploy")
                                    }
                                    dialog.dismiss()
                                }
                            } else {
                                dialog.dismiss()
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
            } finally {
                if (!completed) {
                    withContext(Dispatchers.Main) {
                        if (dialog.isShowing) dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fM", bytes / (1024.0 * 1024.0))
    }
}
