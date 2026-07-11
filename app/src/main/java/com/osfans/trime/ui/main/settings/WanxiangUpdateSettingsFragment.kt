/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.main.settings

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.ResourceUrls
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.wanxiang.DeployTarget
import com.osfans.trime.data.wanxiang.DownloadManager
import com.osfans.trime.data.wanxiang.TaskState
import com.osfans.trime.data.wanxiang.loadDeployTargets
import com.osfans.trime.data.wanxiang.saveDeployTargets
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.compareVersions
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.util.formatUpdatedAt
import com.osfans.trime.util.readLocalWanxiangVersion
import com.osfans.trime.worker.WanxiangCheckWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class WanxiangUpdateSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().wanxiang) {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.defaultInstance().wanxiang
    private val summaryKeys = setOf(
        AppPrefs.Wanxiang.LAST_DICT_SHA256,
        AppPrefs.Wanxiang.LAST_DICT_UPDATED_AT,
        AppPrefs.Wanxiang.NEEDS_UPDATE_DICT,
        AppPrefs.Wanxiang.LAST_MODEL_SHA256,
        AppPrefs.Wanxiang.LAST_MODEL_UPDATED_AT,
        AppPrefs.Wanxiang.NEEDS_UPDATE_MODEL,
    )
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in summaryKeys) {
            lifecycleScope.launch(Dispatchers.Main) {
                refreshSwitchSummaries()
            }
        }
    }

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

    private lateinit var schemaSwitchPref: SwitchPreferenceCompat
    private lateinit var dictSwitchPref: SwitchPreferenceCompat
    private lateinit var modelSwitchPref: SwitchPreferenceCompat

    private lateinit var versionDisplayPref: Preference

    private var currentDeployTargets = mutableListOf<DeployTarget>()
    private var deployTargetsDialog: AlertDialog? = null
    private var deployTargetsContainer: LinearLayout? = null

    private val dirPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val pathStr = uri.toString()
            if (currentDeployTargets.none { it.path == pathStr }) {
                currentDeployTargets.add(DeployTarget(pathStr, true))
            }
            rebuildDeployTargetsList()
        }
    }

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
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(prefsChangeListener)
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
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
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

                schemaSwitchPref = SwitchPreferenceCompat(ctx).apply {
                    key = AppPrefs.Wanxiang.CHECK_SCHEMA
                    title = getString(R.string.wanxiang_schema_only)
                    isChecked = prefs.checkSchema.getValue()
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.checkSchema.setValue(newValue as Boolean)
                        true
                    }
                }
                addPreference(schemaSwitchPref)
                dictSwitchPref = SwitchPreferenceCompat(ctx).apply {
                    key = AppPrefs.Wanxiang.CHECK_DICT
                    title = getString(R.string.wanxiang_dict_only)
                    isChecked = prefs.checkDict.getValue()
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.checkDict.setValue(newValue as Boolean)
                        true
                    }
                }
                addPreference(dictSwitchPref)
                modelSwitchPref = SwitchPreferenceCompat(ctx).apply {
                    key = AppPrefs.Wanxiang.CHECK_MODEL
                    title = getString(R.string.wanxiang_model_only)
                    isChecked = prefs.checkModel.getValue()
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    setOnPreferenceChangeListener { _, newValue ->
                        prefs.checkModel.setValue(newValue as Boolean)
                        true
                    }
                }
                addPreference(modelSwitchPref)
            }
        }

        refreshLocalVersion()
        fetchLatestVersionTag()
        refreshSwitchSummaries()
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

    private fun buildHashSummary(sha: String, updatedAt: String, needsUpdate: Boolean): CharSequence {
        if (sha.isEmpty() && updatedAt.isEmpty()) return getString(R.string.wanxiang_no_hash)
        val sb = StringBuilder()
        if (updatedAt.isNotEmpty()) {
            sb.append(getString(R.string.wanxiang_last_updated_at, updatedAt))
            if (sha.isNotEmpty()) sb.append('\n')
        }
        if (sha.isNotEmpty()) {
            sb.append(getString(R.string.wanxiang_current_hash, sha.take(12)))
        }
        val text = sb.toString()
        if (text.isEmpty()) return getString(R.string.wanxiang_no_hash)
        val color = requireContext().getColor(
            if (needsUpdate) R.color.red else R.color.green,
        )
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, text.length, 0)
        }
    }

    private fun refreshSwitchSummaries() {
        dictSwitchPref.summary = buildHashSummary(
            prefs.lastDictSha256.getValue(),
            prefs.lastDictUpdatedAt.getValue(),
            prefs.needsUpdateDict.getValue(),
        )
        modelSwitchPref.summary = buildHashSummary(
            prefs.lastModelSha256.getValue(),
            prefs.lastModelUpdatedAt.getValue(),
            prefs.needsUpdateModel.getValue(),
        )
    }

    private fun fetchLatestVersionTag(notifyOnNew: Boolean = false) {
        lifecycleScope.launch {
            val source = prefs.downloadSource.getValue()
            val token = if (source == "CNB") prefs.cnbToken.getValue() else prefs.ghToken.getValue()
            val json = ResourceUrls.fetchReleaseJson(ResourceUrls.schemaApi(source), token, client)
            val tag = json?.optString("tag_name", "") ?: ""
            if (tag.isNotEmpty()) latestStableTag = tag
            withContext(Dispatchers.Main) {
                updateVersionDisplay()
                val isNewer = compareVersions(latestStableTag, currentLocalVersion) > 0
                if (notifyOnNew && isNewer) {
                    showUpdateNotification()
                }
            }
        }
    }

    private fun showUpdateNotification() {
        val context = requireContext()
        val channelId = "wanxiang-update-check"
        createNotificationChannel(channelId, context.getString(R.string.wanxiang_updater))

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_RUN
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
        if (!prefs.checkSchema.getValue() && !prefs.checkDict.getValue() && !prefs.checkModel.getValue()) return
        if (isDownloading) return

        val names = mutableListOf<String>()
        if (prefs.checkSchema.getValue()) names.add(getString(R.string.wanxiang_schema_only))
        if (prefs.checkDict.getValue()) names.add(getString(R.string.wanxiang_dict_only))
        if (prefs.checkModel.getValue()) names.add(getString(R.string.wanxiang_model_only))
        val name = names.joinToString(" ")

        showDeployTargetsDialog(name)
    }

    private fun showDeployTargetsDialog(updateName: String) {
        currentDeployTargets = loadDeployTargets(prefs.deployTargets.getValue())
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
        }

        val messageText = TextView(ctx).apply {
            text = getString(R.string.wanxiang_will_update, updateName)
            textSize = 14f
            setPadding(0, 0, 0, 12.dp())
        }
        root.addView(messageText)

        deployTargetsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scrollView = ScrollView(ctx).apply {
            addView(deployTargetsContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val forceUpdateCheckBox = CheckBox(ctx).apply {
            text = getString(R.string.wanxiang_force_update)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 12.dp()
                bottomMargin = 8.dp()
            }
        }
        root.addView(forceUpdateCheckBox)

        val buttonBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 12.dp(), 0, 0)
        }

        val dialog = ctx.buildDialog(R.string.wanxiang_deploy_targets_title)
            .setView(root)
            .setCancelable(true)
            .create()

        val cancelBtn = android.widget.Button(ctx, null, android.R.attr.buttonBarButtonStyle).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }
        buttonBar.addView(cancelBtn)

        val addBtn = android.widget.Button(ctx, null, android.R.attr.buttonBarButtonStyle).apply {
            text = getString(R.string.wanxiang_deploy_add)
            setOnClickListener { dirPickerLauncher.launch(null) }
        }
        buttonBar.addView(addBtn)

        val confirmBtn = android.widget.Button(ctx, null, android.R.attr.buttonBarButtonStyle).apply {
            text = getString(R.string.wanxiang_deploy_confirm)
            setOnClickListener {
                val enabledPaths = currentDeployTargets
                    .filter { it.enabled }
                    .map { it.path }
                if (enabledPaths.isEmpty()) {
                    android.widget.Toast.makeText(
                        ctx,
                        R.string.wanxiang_deploy_no_targets,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    return@setOnClickListener
                }
                saveTargetsToPrefs()
                dialog.dismiss()
                lifecycleScope.launch { doExecuteSelected(enabledPaths, forceUpdateCheckBox.isChecked) }
            }
        }
        buttonBar.addView(confirmBtn)

        root.addView(buttonBar)

        dialog.setOnDismissListener {
            deployTargetsContainer = null
            deployTargetsDialog = null
        }

        deployTargetsDialog = dialog
        rebuildDeployTargetsList()
        dialog.show()
    }

    private fun uriToDisplayPath(uriString: String): String = try {
        val uri = Uri.parse(uriString)
        val docId = DocumentsContract.getTreeDocumentId(uri)
        docId.substringAfter(":").ifEmpty { docId }
    } catch (_: Exception) {
        uriString
    }

    private fun rebuildDeployTargetsList() {
        val container = deployTargetsContainer ?: return
        container.removeAllViews()
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        for ((index, target) in currentDeployTargets.withIndex()) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp(), 0, 4.dp())
            }

            val checkBox = CheckBox(ctx).apply {
                isChecked = target.enabled
                setOnCheckedChangeListener { _, isChecked ->
                    currentDeployTargets[index] = target.copy(enabled = isChecked)
                }
            }
            row.addView(checkBox)

            val label = TextView(ctx).apply {
                text = uriToDisplayPath(target.path)
                textSize = 13f
                setTextColor(ctx.getColor(R.color.text))
                setPadding(8.dp(), 0, 8.dp(), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)

            val deleteBtn = ImageButton(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
                setImageResource(R.drawable.ic_baseline_delete_24)
                imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.text))
                setBackgroundResource(android.R.color.transparent)
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setMessage(R.string.wanxiang_deploy_delete_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            currentDeployTargets.removeAt(index)
                            rebuildDeployTargetsList()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            row.addView(deleteBtn)

            container.addView(row)
        }

        if (currentDeployTargets.isEmpty()) {
            val hint = TextView(ctx).apply {
                text = getString(R.string.wanxiang_deploy_no_targets)
                textSize = 13f
                setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
            }
            container.addView(hint)
        }
    }

    private fun saveTargetsToPrefs() {
        prefs.deployTargets.setValue(saveDeployTargets(currentDeployTargets))
    }

    private suspend fun doExecuteSelected(targetPaths: List<String>, forceUpdate: Boolean = false) {
        val isPro = prefs.isPro.getValue()
        val auxScheme = prefs.auxScheme.getValue()
        val downloadSource = prefs.downloadSource.getValue()
        val updateChannel = prefs.updateChannel.getValue()

        val schemeStr = when (isPro) {
            "pro" -> auxScheme
            "pure" -> "pure"
            else -> "base"
        }

        val token = if (downloadSource == "CNB") {
            prefs.cnbToken.getValue()
        } else {
            prefs.ghToken.getValue()
        }

        if (downloadSource == "CNB" && token.isEmpty()) {
            withContext(Dispatchers.Main) {
                requireContext().buildDialog()
                    .setMessage(R.string.wanxiang_cnb_token_required)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }

        val schemaApiUrl = if (updateChannel == "Stable") ResourceUrls.schemaApi(downloadSource) else ResourceUrls.dictApi(downloadSource)
        val dictApiUrl = ResourceUrls.dictApi(downloadSource)
        val modelApiUrl = ResourceUrls.modelApi(downloadSource)

        val schemaAssetName = when (isPro) {
            "pro" -> "rime-wanxiang-$schemeStr-fuzhu.zip"
            else -> "rime-wanxiang-$schemeStr.zip"
        }

        val dictAssetName = when (isPro) {
            "pro" -> "pro-$schemeStr-fuzhu-dicts.zip"
            else -> "$schemeStr-dicts.zip"
        }

        val modelAssetName = "wanxiang-lts-zh-hans.gram"

        val checkSchema = prefs.checkSchema.getValue()
        val checkDict = prefs.checkDict.getValue()
        val checkModel = prefs.checkModel.getValue()

        val urls = mutableListOf<String>()
        val expectedShas = mutableMapOf<String, String>()
        val releaseUpdatedAts = mutableMapOf<String, String>()
        var modelUpToDate = false
        var dictUpToDate = false

        withContext(Dispatchers.IO) {
            if (checkSchema) {
                val schemaJson = ResourceUrls.fetchReleaseJson(schemaApiUrl, token, client)
                val asset = schemaJson?.let { ResourceUrls.findAssetByName(it, schemaAssetName, downloadSource) }
                if (asset != null && asset.downloadUrl.isNotEmpty()) {
                    urls.add(asset.downloadUrl)
                    expectedShas[asset.name] = asset.sha256
                }
            }

            if (checkDict) {
                val dictJson = ResourceUrls.fetchReleaseJson(dictApiUrl, token, client)
                val asset = dictJson?.let { ResourceUrls.findAssetByName(it, dictAssetName, downloadSource) }
                if (asset != null && asset.downloadUrl.isNotEmpty()) {
                    val storedSha = prefs.lastDictSha256.getValue()
                    if (forceUpdate || storedSha.isEmpty() || storedSha != asset.sha256) {
                        urls.add(asset.downloadUrl)
                    } else {
                        dictUpToDate = true
                    }
                    expectedShas[asset.name] = asset.sha256
                    releaseUpdatedAts[asset.name] = formatUpdatedAt(asset.releaseUpdatedAt)
                }
            }

            if (checkModel) {
                val modelJson = ResourceUrls.fetchReleaseJson(modelApiUrl, token, client)
                val asset = modelJson?.let { ResourceUrls.findAssetByName(it, modelAssetName, downloadSource) }
                if (asset != null && asset.downloadUrl.isNotEmpty()) {
                    val storedSha = prefs.lastModelSha256.getValue()
                    if (forceUpdate || storedSha.isEmpty() || storedSha != asset.sha256) {
                        urls.add(asset.downloadUrl)
                    } else {
                        modelUpToDate = true
                    }
                    expectedShas[asset.name] = asset.sha256
                    releaseUpdatedAts[asset.name] = formatUpdatedAt(asset.releaseUpdatedAt)
                }
            }
        }

        if (checkDict && !forceUpdate && dictUpToDate) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.wanxiang_dict_up_to_date,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        if (checkModel && !forceUpdate && modelUpToDate) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.wanxiang_model_up_to_date,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        if (urls.isEmpty()) return
        val rules = prefs.excludeRules.getValue().lines().filter { it.isNotBlank() }
        startDownload(urls, rules, token, expectedShas, releaseUpdatedAts, targetPaths)
    }

    private fun startDownload(
        urls: List<String>,
        rules: List<String>,
        githubToken: String,
        expectedShas: Map<String, String> = emptyMap(),
        releaseUpdatedAts: Map<String, String> = emptyMap(),
        targetPaths: List<String> = emptyList(),
    ) {
        if (isDownloading) return
        isDownloading = true
        viewModel.disableToolbarDeleteButton()

        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val taskContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 8.dp())
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
            TaskState(title, url, expectedSha256 = expectedShas[fName], releaseUpdatedAt = releaseUpdatedAts[fName])
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
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
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
                        targetPaths = targetPaths,
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
                if (allSucceeded) {
                    for (task in tasks) {
                        val sha = task.expectedSha256 ?: continue
                        when {
                            task.url.contains("dicts") -> {
                                prefs.lastDictSha256.setValue(sha)
                                prefs.needsUpdateDict.setValue(false)
                                task.releaseUpdatedAt?.let { prefs.lastDictUpdatedAt.setValue(it) }
                            }
                            task.url.contains(".gram") -> {
                                prefs.lastModelSha256.setValue(sha)
                                prefs.needsUpdateModel.setValue(false)
                                task.releaseUpdatedAt?.let { prefs.lastModelUpdatedAt.setValue(it) }
                            }
                        }
                    }
                }
                completed = true
                withContext(Dispatchers.Main) {
                    if (allSucceeded) refreshSwitchSummaries()
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
