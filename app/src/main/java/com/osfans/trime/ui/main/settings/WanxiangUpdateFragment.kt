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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.wanxiang.DownloadManager
import com.osfans.trime.data.wanxiang.TaskState
import com.osfans.trime.data.wanxiang.compareVersions
import com.osfans.trime.data.wanxiang.readLocalWanxiangVersion
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.computeFileSha256
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.worker.WanxiangCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class WanxiangUpdateFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.defaultInstance().wanxiang

    private var isDownloading = false
    private var downloadJob: Job? = null
    private var latestStableTag = "v1.0.0"
    private var currentLocalVersion = "v0.0.0"

    private var checkSchema = true
    private var checkDict = false
    private var checkModel = false

    private lateinit var versionDisplayPref: Preference
    private lateinit var auxSchemePref: ListPreference
    private lateinit var checkIntervalPref: DialogSeekBarPreference
    private lateinit var ghTokenPref: EditTextPreference

    private val onIsProChange = PreferenceDelegate.OnChangeListener<String> { _, _ ->
        auxSchemePref.isEnabled = prefs.isPro.getValue() == "pro"
    }

    private val onAutoCheckChange = PreferenceDelegate.OnChangeListener<Boolean> { _, enabled ->
        checkIntervalPref.isEnabled = enabled
        if (enabled) {
            WanxiangCheckWorker.start(requireContext())
        } else {
            WanxiangCheckWorker.cancel(requireContext())
        }
    }

    private val onCheckIntervalChange = PreferenceDelegate.OnChangeListener<Int> { _, _ ->
        WanxiangCheckWorker.start(requireContext())
    }

    private val onDownloadSourceChange = PreferenceDelegate.OnChangeListener<String> { _, source ->
        ghTokenPref.isEnabled = source == "GitHub"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.isPro.registerOnChangeListener(onIsProChange)
        prefs.autoCheck.registerOnChangeListener(onAutoCheckChange)
        prefs.checkInterval.registerOnChangeListener(onCheckIntervalChange)
        prefs.downloadSource.registerOnChangeListener(onDownloadSourceChange)
    }

    override fun onStart() {
        super.onStart()
        viewModel.enableToolbarEditButton(
            icon = R.drawable.ic_baseline_refresh_reversed_24,
        ) {
            refreshLocalVersion()
            fetchLatestVersionTag(notifyOnNew = true)
            WanxiangCheckWorker.start(requireContext())
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
        super.onDestroy()
        prefs.isPro.unregisterOnChangeListener(onIsProChange)
        prefs.autoCheck.unregisterOnChangeListener(onAutoCheckChange)
        prefs.checkInterval.unregisterOnChangeListener(onCheckIntervalChange)
        prefs.downloadSource.unregisterOnChangeListener(onDownloadSourceChange)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            isIconSpaceReserved = false

            versionDisplayPref = Preference(ctx).apply {
                key = "wanxiang_version_display"
                title = getString(R.string.wanxiang_scheme_version)
                isIconSpaceReserved = false
                isSingleLineTitle = false
            }
            addPreference(versionDisplayPref)

            addCategory(R.string.wanxiang_updater) {
                isIconSpaceReserved = false

                addPreference(
                    ListPreference(ctx).apply {
                        key = AppPrefs.Wanxiang.UPDATE_CHANNEL
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        entryValues = arrayOf("Stable", "Preview")
                        entries = arrayOf(
                            getString(R.string.wanxiang_stable),
                            getString(R.string.wanxiang_preview),
                        )
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        setDefaultValue("Stable")
                        setTitle(R.string.wanxiang_update_channel)
                        setDialogTitle(R.string.wanxiang_update_channel)
                    },
                )

                addPreference(
                    ListPreference(ctx).apply {
                        key = AppPrefs.Wanxiang.IS_PRO
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        entryValues = arrayOf("pro", "base")
                        entries = arrayOf("Pro", "Base")
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        setDefaultValue("pro")
                        setTitle(R.string.wanxiang_scheme_version)
                        setDialogTitle(R.string.wanxiang_scheme_version)
                    },
                )

                auxSchemePref = ListPreference(ctx).apply {
                    key = AppPrefs.Wanxiang.AUX_SCHEME
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    entryValues = arrayOf(
                        "zrm", "wx", "flypy", "moqi",
                        "hanxin", "shouyou", "shyplus", "tiger", "wubi",
                    )
                    entries = arrayOf(
                        "自然码", "万象", "小鹤", "墨奇",
                        "汉心", "首右", "首右+", "虎码", "五笔",
                    )
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    setDefaultValue("zrm")
                    setTitle(R.string.wanxiang_aux_scheme)
                    setDialogTitle(R.string.wanxiang_aux_scheme)
                    isEnabled = prefs.isPro.getValue() == "pro"
                }
                addPreference(auxSchemePref)

                addPreference(
                    SwitchPreference(ctx).apply {
                        key = AppPrefs.Wanxiang.AUTO_CHECK
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        setDefaultValue(false)
                        setTitle(R.string.wanxiang_auto_check)
                    },
                )

                checkIntervalPref = DialogSeekBarPreference(ctx).apply {
                    key = AppPrefs.Wanxiang.CHECK_INTERVAL
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    summaryProvider = DialogSeekBarPreference.SimpleSummaryProvider
                    setDefaultValue(12)
                    setTitle(R.string.wanxiang_check_interval)
                    min = 3
                    max = 24
                    unit = "h"
                    step = 1
                    isEnabled = prefs.autoCheck.getValue()
                }
                addPreference(checkIntervalPref)

                addPreference(
                    EditTextPreference(ctx).apply {
                        key = AppPrefs.Wanxiang.EXCLUDE_RULES
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                            val text = pref.sharedPreferences?.getString(pref.key, "") ?: ""
                            val count = text.lines().count { it.isNotBlank() }
                            if (count == 0) {
                                getString(R.string.disable)
                            } else {
                                getString(R.string.wanxiang_exclude_rules_count, count)
                            }
                        }
                        setDefaultValue(prefs.excludeRules.getValue())
                        setTitle(R.string.wanxiang_exclude_rules)
                        setDialogTitle(R.string.wanxiang_exclude_rules)
                        setDialogMessage(R.string.wanxiang_exclude_hint)
                        setOnBindEditTextListener {
                            it.typeface = Typeface.MONOSPACE
                            it.textSize = 15f
                        }
                    },
                )

                addPreference(
                    ListPreference(ctx).apply {
                        key = AppPrefs.Wanxiang.DOWNLOAD_SOURCE
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        entryValues = arrayOf("CNB", "GitHub")
                        entries = arrayOf("CNB", "GitHub")
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        setDefaultValue("CNB")
                        setTitle(R.string.wanxiang_download_source)
                        setDialogTitle(R.string.wanxiang_download_source)
                    },
                )

                ghTokenPref = EditTextPreference(ctx).apply {
                    key = AppPrefs.Wanxiang.GH_TOKEN
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    setDefaultValue("")
                    setTitle(R.string.wanxiang_github_token)
                    setDialogTitle(R.string.wanxiang_github_token)
                    isEnabled = prefs.downloadSource.getValue() == "GitHub"
                }
                addPreference(ghTokenPref)
            }

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
        val variant = if (isPro == "pro") "Pro" else "Base"
        versionDisplayPref.summary = getString(
            R.string.wanxiang_version_label_fmt,
            "$channelLabel ($variant)",
            "$currentLocalVersion → $latestStableTag",
        )
    }

    private fun fetchLatestVersionTag(notifyOnNew: Boolean = false) {
        lifecycleScope.launch {
            var rateLimited = false
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(ResourceUrls.WANXIANG_API_LATEST_RELEASE)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "WanxiangUpdater-Agent")
                    conn.connectTimeout = 10000
                    conn.connect()
                    if (conn.responseCode == 200) {
                        val content = conn.inputStream.bufferedReader().readText()
                        val tag = JSONObject(content).optString("tag_name", "")
                        if (tag.isNotEmpty()) latestStableTag = tag
                    } else if (conn.responseCode in setOf(403, 429)) {
                        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                        if (body.contains("rate limit", ignoreCase = true) || body.contains("API rate limit", ignoreCase = true)) {
                            rateLimited = true
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

        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(getString(R.string.wanxiang_confirm_execute, DataManager.userDataDir.absolutePath))
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

        val schemeStr = if (isPro == "pro") auxScheme else "base"
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
            urls.add("$dictBaseUrl/${if (isPro == "pro") "pro-$schemeStr-fuzhu" else "base"}-dicts.zip")
        }
        if (checkModel) {
            val result = withContext(Dispatchers.IO) { checkModelUpdate() }
            when (result) {
                ModelCheckResult.FETCH_FAILED -> {
                    val proceed = suspendCancellableCoroutine { cont ->
                        AlertDialog.Builder(requireContext())
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
        val schemaFile = "rime-wanxiang-$schemeStr${if (isPro == "pro") "-fuzhu" else ""}.zip"
        val schemaAssets = fetchReleaseAssetsSha256(schemaTag)
        schemaAssets[schemaFile]?.let { result[schemaFile] = it }
        val dictFile = "${if (isPro == "pro") "pro-$schemeStr-fuzhu" else "base"}-dicts.zip"
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
            val url = URL(ResourceUrls.WANXIANG_API_RIME_LMDG_TAGS_LTS)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "WanxiangUpdater-Agent")
            conn.connectTimeout = 10000
            conn.connect()
            if (conn.responseCode != 200) return null
            val content = conn.inputStream.bufferedReader().readText()
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
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchReleaseAssetsSha256(tag: String): Map<String, String> {
        return try {
            val url = URL("https://api.github.com/repos/amzxyz/rime-wanxiang/releases/tags/$tag")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "WanxiangUpdater-Agent")
            conn.connectTimeout = 10000
            conn.connect()
            if (conn.responseCode != 200) return emptyMap()
            val content = conn.inputStream.bufferedReader().readText()
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

        val progressViews = tasks.map { task ->
            TextView(ctx).apply {
                text = task.title
                setPadding(0, 8.dp(), 0, 8.dp())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }.also { taskContainer.addView(it) }
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.wanxiang_task_progress)
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
                                val status = if (t.progress >= 0f) {
                                    getString(R.string.wanxiang_dl_progress_status, (t.progress * 100).toInt(), t.status)
                                } else {
                                    t.status.ifEmpty { "…" }
                                }
                                progressViews[i].text = getString(
                                    R.string.wanxiang_dl_progress_line,
                                    task.title,
                                    status,
                                )
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
}
