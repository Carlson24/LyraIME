/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.amzxyz.wanxiang

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.createNotificationChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume

class WanxiangUpdateFragment : Fragment() {

    private val sharedPref by lazy { requireContext().getSharedPreferences("WanxiangPrefs", 0) }
    private val viewModel: MainViewModel by activityViewModels()
    private var isDownloading = false
    private var githubToken = ""
    private var latestStableTag = "v1.0.0"
    private var currentLocalVersion = "v0.0.0"

    private val auxMap = mapOf(
        "zrm" to "自然码", "wx" to "万象", "flypy" to "小鹤", "moqi" to "墨奇",
        "hanxin" to "汉心", "shouyou" to "首右", "shyplus" to "首右+",
        "tiger" to "虎码", "wubi" to "五笔",
    )

    // Dynamic views
    private lateinit var rootLayout: LinearLayout
    private lateinit var tvVersionValue: TextView
    private lateinit var tvVersionLabel: TextView
    private lateinit var tvChannelValue: TextView
    private lateinit var tvAuxValue: TextView
    private lateinit var rowAux: LinearLayout
    private lateinit var dividerAfterAux: View
    private lateinit var tvWhitelistSummary: TextView
    private lateinit var tvSourceValue: TextView
    private lateinit var swAutoCheck: SwitchCompat
    private lateinit var llCheckInterval: LinearLayout
    private lateinit var sbCheckInterval: SeekBar
    private lateinit var tvCheckInterval: TextView
    private lateinit var etToken: EditText
    private lateinit var cvProgress: LinearLayout
    private lateinit var llProgressItems: LinearLayout
    private var checkSchema = true
    private var checkDict = false
    private var checkModel = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(buildContent(ctx))
        }
        rootLayout.addView(scrollView)
        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadSettings()
        readLocalWanxiangVersion()
        fetchLatestVersionTag()
        setupAutoCheck()
        setupConfigSection()
        setupWhitelistRow()
        setupSourceSection()
    }

    override fun onStart() {
        super.onStart()
        viewModel.enableToolbarEditButton(
            icon = R.drawable.ic_baseline_refresh_reversed_24,
        ) {
            tvVersionValue.text = "..."
            readLocalWanxiangVersion()
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

    // ---- Build UI programmatically ----

    private fun buildContent(ctx: Context): LinearLayout {
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 32.dp())

            // Update Channel
            buildClickableRow(ctx, R.string.wanxiang_update_channel).also { row ->
                tvChannelValue = rowValue(row)
                row.setOnClickListener { showChannelDialog() }
                addView(row)
            }
            addView(divider(ctx))

            // Scheme Version
            buildClickableRow(ctx, 0).also { row ->
                tvVersionLabel = rowLabel(row)
                tvVersionValue = rowValue(row)
                row.setOnClickListener { showVersionDialog() }
                addView(row)
            }
            addView(divider(ctx))

            // Aux Scheme
            rowAux = buildClickableRow(ctx, R.string.wanxiang_aux_scheme).also { row ->
                tvAuxValue = rowValue(row)
                row.setOnClickListener { showAuxDialog() }
                row.visibility = View.GONE
                addView(row)
            }
            dividerAfterAux = divider(ctx).also {
                it.visibility = View.GONE
                addView(it)
            }

            // Auto Check
            buildRow(ctx).also { row ->
                row.addView(rowLabel(ctx, R.string.wanxiang_auto_check))
                swAutoCheck = SwitchCompat(ctx).also { row.addView(it) }
                addView(row)
            }

            llCheckInterval = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dp(), 0, 0)
                visibility = View.GONE
                tvCheckInterval = secondaryText(ctx).also { addView(it) }
                sbCheckInterval = SeekBar(ctx).apply {
                    max = 21
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                addView(sbCheckInterval)
            }
            addView(llCheckInterval)

            addView(divider(ctx))

            // Whitelist
            buildClickableRow(ctx, R.string.wanxiang_advanced_rules).also { row ->
                tvWhitelistSummary = rowValue(row)
                row.setOnClickListener { showWhitelistDialog() }
                addView(row)
            }
            addView(divider(ctx))

            // Download Source
            buildClickableRow(ctx, R.string.wanxiang_download_source).also { row ->
                tvSourceValue = rowValue(row)
                row.setOnClickListener { showSourceDialog() }
                addView(row)
            }

            etToken = EditText(ctx).apply {
                hint = ctx.getString(R.string.wanxiang_github_token)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                maxLines = 1
                visibility = View.GONE
                setOnFocusChangeListener { _, _ ->
                    githubToken = text.toString()
                    sharedPref.edit { putString("gh_token", githubToken) }
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 8.dp() }
            }
            addView(etToken)

            addView(divider(ctx))

            // Progress section
            cvProgress = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                addView(sectionLabel(ctx, R.string.wanxiang_task_progress))
                llProgressItems = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(llProgressItems)
                addView(divider(ctx))
            }
            addView(cvProgress)

            // Action section
            addView(sectionLabel(ctx, R.string.wanxiang_actions))
            val checksRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val schemaCb = buildCheckRow(ctx, R.string.wanxiang_schema_only, checkSchema) { checkSchema = it }
            checksRow.addView(schemaCb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val dictCb = buildCheckRow(ctx, R.string.wanxiang_dict_only, checkDict) { checkDict = it }
            checksRow.addView(dictCb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val modelCb = buildCheckRow(ctx, R.string.wanxiang_model_only, checkModel) { checkModel = it }
            checksRow.addView(modelCb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(checksRow)

            addView(divider(ctx))
        }
    }

    // ---- UI Helpers ----

    private fun sectionLabel(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItem))
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 4.dp() }
    }

    private fun sectionLabel(ctx: Context, resId: Int): TextView = sectionLabel(ctx, ctx.getString(resId))

    private fun secondaryText(ctx: Context): TextView = TextView(ctx).apply {
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItemSecondary))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 8.dp() }
    }

    private fun divider(ctx: Context): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            1,
        ).apply {
            topMargin = 12.dp()
            bottomMargin = 4.dp()
        }
        setBackgroundColor(listDividerColor(ctx))
    }

    private fun buildRow(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 48.dp()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun buildClickableRow(ctx: Context, titleRes: Int): LinearLayout = buildRow(ctx).apply {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        setBackgroundResource(tv.resourceId)
        isClickable = true
        isFocusable = true
        if (titleRes != 0) {
            addView(rowLabel(ctx, titleRes))
        } else {
            addView(
                TextView(ctx).apply {
                    TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItem))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
        }
        addView(
            TextView(ctx).apply {
                TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItemSecondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )
    }

    private fun rowLabel(row: LinearLayout): TextView = row.getChildAt(0) as TextView
    private fun rowValue(row: LinearLayout): TextView = row.getChildAt(1) as TextView

    private fun rowLabel(ctx: Context, titleRes: Int): TextView = TextView(ctx).apply {
        setText(titleRes)
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItem))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun buildCheckRow(ctx: Context, titleRes: Int, checked: Boolean, onToggle: (Boolean) -> Unit): LinearLayout = buildRow(ctx).apply {
        setOnClickListener {
            val newChecked = !(tag as? Boolean ?: false)
            tag = newChecked
            onToggle(newChecked)
            val checkView = getChildAt(0) as? TextView ?: return@setOnClickListener
            updateCheckDrawable(checkView, newChecked)
        }
        tag = checked
        val checkBox = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { marginEnd = 12.dp() }
        }
        updateCheckDrawable(checkBox, checked)
        addView(checkBox)
        addView(rowLabel(ctx, titleRes))
    }

    private fun updateCheckDrawable(view: TextView, checked: Boolean) {
        val ctx = view.context
        val dp = ctx.resources.displayMetrics.density
        val accent = accentColor(ctx)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (4 * dp)
            if (checked) {
                setColor(accent)
            } else {
                setColor(Color.argb(0x18, Color.red(accent), Color.green(accent), Color.blue(accent)))
                setStroke(2.dp(), accent)
            }
        }
        view.text = if (checked) "✓" else ""
        view.setTextColor(if (checked) color(android.R.color.white) else accent)
    }

    // ---- Settings loading ----

    private fun loadSettings() {
        githubToken = sharedPref.getString("gh_token", "") ?: ""
        val isPro = sharedPref.getBoolean("is_pro", true)
        val auxScheme = sharedPref.getString("aux_scheme", "zrm") ?: "zrm"
        val downloadSource = sharedPref.getString("download_source", "CNB") ?: "CNB"
        val updateChannel = sharedPref.getString("update_channel", "Stable") ?: "Stable"
        val excludeRules = sharedPref.getString("exclude_rules", DefaultExcludeRules) ?: DefaultExcludeRules

        etToken.setText(githubToken)
        etToken.visibility = if (downloadSource == "GitHub") View.VISIBLE else View.GONE

        updateChannelDisplay(updateChannel)
        updateVersionDisplay(isPro)
        updateAuxDisplay(auxScheme, isPro)
        updateWhitelistSummary(excludeRules)
        updateSourceDisplay(downloadSource)
    }

    private fun readLocalWanxiangVersion() {
        currentLocalVersion = try {
            val userDataFile = File(DataManager.userDataDir, "lua/wanxiang/wanxiang.lua")
            val sharedDataFile = File(DataManager.sharedDataDir, "lua/wanxiang/wanxiang.lua")
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

    // ---- Display updates ----

    private fun updateChannelDisplay(channel: String) {
        tvChannelValue.text = if (channel == "Stable") getString(R.string.wanxiang_stable) else getString(R.string.wanxiang_preview)
    }

    private fun updateVersionDisplay(isPro: Boolean) {
        val current = currentLocalVersion
        val updateChannel = sharedPref.getString("update_channel", "Stable") ?: "Stable"
        val remote = if (updateChannel == "Stable") latestStableTag else "beta"
        tvVersionLabel.text = getString(R.string.wanxiang_version_label_fmt, getString(R.string.wanxiang_scheme_version), current)
        tvVersionValue.text = if (isPro) "Pro $remote" else "Base $remote"
    }

    private fun updateAuxDisplay(key: String, show: Boolean) {
        rowAux.visibility = if (show) View.VISIBLE else View.GONE
        dividerAfterAux.visibility = if (show) View.VISIBLE else View.GONE
        tvAuxValue.text = auxMap[key] ?: key
    }

    private fun updateWhitelistSummary(rules: String) {
        val count = rules.lines().count { it.isNotBlank() }
        tvWhitelistSummary.text = if (count == 0) getString(R.string.disable) else getString(R.string.wanxiang_exclude_rules_count, count)
    }

    private fun updateSourceDisplay(source: String) {
        tvSourceValue.text = source
    }

    private fun setupAutoCheck() {
        val autoCheck = sharedPref.getBoolean("auto_check", false)
        val checkInterval = sharedPref.getInt("check_interval", 12)
        swAutoCheck.isChecked = autoCheck
        llCheckInterval.visibility = if (autoCheck) View.VISIBLE else View.GONE
        sbCheckInterval.progress = checkInterval - 3
        tvCheckInterval.text = getString(R.string.wanxiang_check_interval, checkInterval)

        swAutoCheck.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit { putBoolean("auto_check", isChecked) }
            llCheckInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                WanxiangCheckWorker.start(requireContext())
            } else {
                WanxiangCheckWorker.cancel(requireContext())
            }
        }

        sbCheckInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val hours = progress + 3
                tvCheckInterval.text = getString(R.string.wanxiang_check_interval, hours)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val hours = seekBar.progress + 3
                sharedPref.edit { putInt("check_interval", hours) }
                WanxiangCheckWorker.start(requireContext())
            }
        })
    }

    private fun fetchLatestVersionTag(notifyOnNew: Boolean = false) {
        lifecycleScope.launch {
            var rateLimited = false
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://api.github.com/repos/amzxyz/rime-wanxiang/releases/latest")
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
                val isPro = sharedPref.getBoolean("is_pro", true)
                updateVersionDisplay(isPro)
                val isNewer = updateVersionComparison()
                if (notifyOnNew && isNewer) {
                    showUpdateNotification()
                }
                if (rateLimited) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.wanxiang_rate_limit), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateVersionComparison(): Boolean {
        val updateChannel = sharedPref.getString("update_channel", "Stable") ?: "Stable"
        if (updateChannel != "Stable") {
            tvVersionValue.setTextColor(color(R.color.wanxiang_accent))
            return true
        }
        val current = currentLocalVersion
        val remote = latestStableTag
        val cmp = compareVersions(remote, current)
        val colorRes = when {
            cmp > 0 -> R.color.wanxiang_accent
            cmp < 0 -> R.color.wanxiang_error
            else -> null
        }
        val defaultColor = tvVersionValue.textColors.defaultColor
        val versionColor = if (colorRes != null) color(colorRes) else defaultColor
        tvVersionValue.setTextColor(versionColor)
        return cmp > 0
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

    // ---- Dialog helpers ----

    private fun setupConfigSection() {
        // Channel, Version, Aux are handled via click listeners in buildContent
    }

    private fun showChannelDialog() {
        val items = arrayOf(getString(R.string.wanxiang_stable), getString(R.string.wanxiang_preview))
        val current = sharedPref.getString("update_channel", "Stable") ?: "Stable"
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wanxiang_update_channel)
            .setSingleChoiceItems(items, if (current == "Stable") 0 else 1) { dialog, which ->
                val channel = if (which == 0) "Stable" else "Preview"
                sharedPref.edit { putString("update_channel", channel) }
                updateChannelDisplay(channel)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showVersionDialog() {
        val tag = latestStableTag
        val items = arrayOf("Pro ($tag)", "Base ($tag)")
        val isPro = sharedPref.getBoolean("is_pro", true)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wanxiang_scheme_version)
            .setSingleChoiceItems(items, if (isPro) 0 else 1) { dialog, which ->
                val newIsPro = which == 0
                sharedPref.edit { putBoolean("is_pro", newIsPro) }
                updateVersionDisplay(newIsPro)
                val auxScheme = sharedPref.getString("aux_scheme", "zrm") ?: "zrm"
                updateAuxDisplay(auxScheme, newIsPro)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAuxDialog() {
        val isPro = sharedPref.getBoolean("is_pro", true)
        if (!isPro) return
        val entries = auxMap.entries.toList()
        val names = entries.map { it.value }.toTypedArray()
        val currentKey = sharedPref.getString("aux_scheme", "zrm") ?: "zrm"
        val selectedIndex = entries.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wanxiang_aux_scheme)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val key = entries[which].key
                sharedPref.edit { putString("aux_scheme", key) }
                updateAuxDisplay(key, true)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupWhitelistRow() {
        // click handled in buildContent
    }

    private fun showWhitelistDialog() {
        val currentRules = sharedPref.getString("exclude_rules", DefaultExcludeRules) ?: DefaultExcludeRules
        val input = EditText(requireContext(), null, android.R.attr.editTextStyle).apply {
            setText(currentRules)
            setHint(R.string.wanxiang_exclude_rules)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP
            minLines = 8
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            addView(
                input,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setPadding(20.dp(), 16.dp(), 20.dp(), 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wanxiang_advanced_rules)
            .setMessage(R.string.wanxiang_exclude_hint)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString()
                sharedPref.edit { putString("exclude_rules", text) }
                updateWhitelistSummary(text)
            }
            .setNeutralButton(R.string.wanxiang_reset_rules) { _, _ ->
                sharedPref.edit { putString("exclude_rules", DefaultExcludeRules) }
                updateWhitelistSummary(DefaultExcludeRules)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupSourceSection() {
        // click handled in buildContent
    }

    private fun showSourceDialog() {
        val items = arrayOf("CNB", "GitHub")
        val current = sharedPref.getString("download_source", "CNB") ?: "CNB"
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wanxiang_download_source)
            .setSingleChoiceItems(items, if (current == "CNB") 0 else 1) { dialog, which ->
                val source = if (which == 0) "CNB" else "GitHub"
                sharedPref.edit { putString("download_source", source) }
                updateSourceDisplay(source)
                etToken.visibility = if (source == "GitHub") View.VISIBLE else View.GONE
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Action ----

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
        val isPro = sharedPref.getBoolean("is_pro", true)
        val auxScheme = sharedPref.getString("aux_scheme", "zrm") ?: "zrm"
        val downloadSource = sharedPref.getString("download_source", "CNB") ?: "CNB"
        val updateChannel = sharedPref.getString("update_channel", "Stable") ?: "Stable"

        val schemeStr = if (isPro) auxScheme else "base"
        val activeTag = if (downloadSource == "CNB") {
            if (updateChannel == "Stable") latestStableTag else "v1.0.0"
        } else {
            if (updateChannel == "Stable") latestStableTag else "dict-nightly"
        }
        val baseUrl = if (downloadSource == "CNB") {
            "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download"
        } else {
            "https://github.com/amzxyz/rime-wanxiang/releases/download"
        }
        val dictBaseUrl = if (downloadSource == "CNB") {
            "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download/v1.0.0"
        } else {
            "https://github.com/amzxyz/rime-wanxiang/releases/download/dict-nightly"
        }
        val modelUrl = if (downloadSource == "CNB") {
            "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download/model/wanxiang-lts-zh-hans.gram"
        } else {
            "https://github.com/amzxyz/RIME-LMDG/releases/download/LTS/wanxiang-lts-zh-hans.gram"
        }

        val urls = mutableListOf<String>()
        if (checkSchema) {
            urls.add("$baseUrl/$activeTag/rime-wanxiang-$schemeStr${if (isPro) "-fuzhu" else ""}.zip")
        }
        if (checkDict) {
            urls.add("$dictBaseUrl/${if (isPro) "pro-$schemeStr-fuzhu" else "base"}-dicts.zip")
        }
        if (checkModel) {
            val result = withContext(Dispatchers.IO) { checkModelUpdate() }
            when (result) {
                ModelCheckResult.FETCH_FAILED -> {
                    val proceed = suspendCancellableCoroutine<Boolean> { cont ->
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
                        android.widget.Toast.makeText(requireContext(), R.string.wanxiang_model_up_to_date, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                ModelCheckResult.NEEDS_UPDATE -> {
                    urls.add(modelUrl)
                }
            }
        }

        if (urls.isEmpty()) return
        val rules = (sharedPref.getString("exclude_rules", DefaultExcludeRules) ?: DefaultExcludeRules)
            .lines().filter { it.isNotBlank() }

        startDownload(urls, rules)
    }

    private enum class ModelCheckResult { FETCH_FAILED, UP_TO_DATE, NEEDS_UPDATE }

    private fun checkModelUpdate(): ModelCheckResult {
        val remoteSha = fetchRemoteModelSha256() ?: return ModelCheckResult.FETCH_FAILED
        val localFile = File(DataManager.userDataDir, "wanxiang-lts-zh-hans.gram")
        if (!localFile.exists()) return ModelCheckResult.NEEDS_UPDATE
        val localSha = computeFileSha256(localFile) ?: return ModelCheckResult.NEEDS_UPDATE
        return if (localSha == remoteSha) ModelCheckResult.UP_TO_DATE else ModelCheckResult.NEEDS_UPDATE
    }

    private fun fetchRemoteModelSha256(): String? {
        return try {
            val url = URL("https://api.github.com/repos/amzxyz/RIME-LMDG/releases/tags/LTS")
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

    private fun startDownload(urls: List<String>, rules: List<String>) {
        if (isDownloading) return
        isDownloading = true
        viewModel.disableToolbarDeleteButton()

        cvProgress.visibility = View.VISIBLE
        llProgressItems.removeAllViews()

        val tasks = urls.map { url ->
            val fName = url.substringAfterLast("/")
            val title = when {
                fName.contains("dicts") -> "${getString(R.string.wanxiang_package_dicts)} ($fName)"
                fName.contains("gram") -> "${getString(R.string.wanxiang_package_model)} ($fName)"
                else -> "${getString(R.string.wanxiang_package_schema)} ($fName)"
            }
            TaskState(title, url)
        }

        for (task in tasks) {
            addProgressItem(task)
        }

        lifecycleScope.launch {
            for (task in tasks) {
                DownloadManager.downloadAndDeploy(
                    task = task,
                    token = githubToken,
                    context = requireContext(),
                    rules = rules,
                    isDict = task.url.contains("dicts"),
                    onProgress = { t ->
                        lifecycleScope.launch(Dispatchers.Main) { updateProgressItem(t) }
                    },
                )
                if (task.isError) break
            }
            if (tasks.none { it.isError }) {
                val session = RimeDaemon.createSession("wanxiang_deploy")
                try {
                    session.runOnReady { deploy() }
                } catch (_: Exception) {
                } finally {
                    RimeDaemon.destroySession("wanxiang_deploy")
                }
            }
            isDownloading = false
            viewModel.enableToolbarDeleteButton(
                icon = R.drawable.ic_baseline_download_24,
            ) {
                if (!isDownloading) executeSelected()
            }
        }
    }

    private fun addProgressItem(task: TaskState) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 8.dp()
                bottomMargin = 4.dp()
            }
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val titleView = TextView(ctx).apply {
            text = task.title
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
        }
        val statusView = TextView(ctx).apply {
            text = task.status
            textSize = 11f
            setTextColor(color(R.color.wanxiang_progress_status))
        }
        header.addView(titleView)
        header.addView(statusView)

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        container.addView(header)
        container.addView(progressBar)
        container.tag = task
        llProgressItems.addView(container)
    }

    private fun updateProgressItem(task: TaskState) {
        for (child in llProgressItems.children) {
            if (child.tag == task && child is LinearLayout) {
                val header = child.getChildAt(0) as? LinearLayout ?: continue
                val bar = child.getChildAt(1) as? ProgressBar ?: continue
                val statusView = header.getChildAt(1) as? TextView ?: continue
                statusView.text = task.status
                if (task.progress < 0) {
                    bar.isIndeterminate = true
                } else {
                    bar.isIndeterminate = false
                    bar.progress = (task.progress * 100).toInt()
                }
                if (task.isError) statusView.setTextColor(color(R.color.wanxiang_error))
            }
        }
    }

    // ---- Utility ----

    private fun computeFileSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun Int.dp(): Int = (this * requireContext().resources.displayMetrics.density).toInt()

    private fun color(@ColorRes resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    private fun listDividerColor(ctx: Context): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.listDivider, tv, true)
        return tv.data
    }

    private fun accentColor(ctx: Context): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)
        return tv.data
    }

    private fun textAppearance(ctx: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) tv.resourceId else tv.data
    }
}
