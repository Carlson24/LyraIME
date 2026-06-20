/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.main.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.wanxiang.CustomTask
import com.osfans.trime.data.wanxiang.DownloadManager
import com.osfans.trime.data.wanxiang.TaskState
import com.osfans.trime.data.wanxiang.addDownloadProgressItem
import com.osfans.trime.data.wanxiang.loadCustomTasks
import com.osfans.trime.data.wanxiang.saveCustomTasks
import com.osfans.trime.data.wanxiang.updateDownloadProgressItem
import com.osfans.trime.ui.common.confirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CustomTasksFragment : Fragment() {

    private val sharedPref by lazy { requireContext().getSharedPreferences("WanxiangPrefs", 0) }
    private var customTasks = mutableListOf<CustomTask>()
    private var isDownloading = false
    private var pendingPathTaskId: String? = null

    private val dirPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val pathStr = uri.toString()
            pendingPathTaskId?.let { taskId ->
                val idx = customTasks.indexOfFirst { it.id == taskId }
                if (idx >= 0) {
                    customTasks[idx] = customTasks[idx].copy(boundPath = pathStr)
                    saveAndRefresh()
                }
            }
            pendingPathTaskId = null
        }
    }

    private lateinit var llCustomTasks: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var cvBatchActions: LinearLayout
    private lateinit var btnBatchExecute: TextView
    private lateinit var btnBatchDelete: TextView
    private lateinit var llCustomProgress: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
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
        root.addView(scrollView)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        customTasks = loadCustomTasks(sharedPref.getString("custom_tasks_data", "[]") ?: "[]")
        refreshTaskList()
    }

    // ---- Build UI ----

    private fun buildContent(ctx: Context): LinearLayout {
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 32.dp())

            addView(sectionLabel(ctx, R.string.wanxiang_custom_title))
            addView(secondaryText(ctx, getString(R.string.wanxiang_custom_desc)))

            cvBatchActions = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 12.dp() }
            }
            run {
                val barRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                barRow.addView(rowLabel(ctx, R.string.wanxiang_batch_queue))
                btnBatchExecute = barActionBtn(ctx, R.string.wanxiang_batch_execute, accentColor(ctx))
                barRow.addView(btnBatchExecute)
                btnBatchDelete = barActionBtn(ctx, R.string.wanxiang_batch_delete, color(R.color.red))
                barRow.addView(btnBatchDelete)
                cvBatchActions.addView(barRow)
                cvBatchActions.addView(
                    divider(ctx).apply {
                        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { topMargin = 8.dp() }
                    },
                )
                llCustomProgress = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = 8.dp() }
                }
                cvBatchActions.addView(llCustomProgress)
            }
            addView(cvBatchActions)

            tvEmpty = secondaryText(ctx, getString(R.string.wanxiang_no_tasks)).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = 20.dp()
                    bottomMargin = 20.dp()
                }
            }
            addView(tvEmpty)

            llCustomTasks = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(llCustomTasks)

            addView(
                Button(ctx).apply {
                    text = getString(R.string.wanxiang_add_task)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        48.dp(),
                    ).apply { topMargin = 12.dp() }
                    setOnClickListener { addNewTask() }
                },
            )
        }
    }

    // ---- UI Helpers ----

    private fun sectionLabel(ctx: Context, resId: Int): TextView = TextView(ctx).apply {
        setText(resId)
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItem))
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 4.dp() }
    }

    private fun secondaryText(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItemSecondary))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 8.dp() }
    }

    private fun divider(ctx: Context): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = 12.dp()
            bottomMargin = 4.dp()
        }
        setBackgroundColor(listDividerColor(ctx))
    }

    private fun rowLabel(ctx: Context, titleRes: Int): TextView = TextView(ctx).apply {
        setText(titleRes)
        TextViewCompat.setTextAppearance(this, textAppearance(ctx, android.R.attr.textAppearanceListItem))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun barActionBtn(ctx: Context, textRes: Int, color: Int): TextView = TextView(ctx).apply {
        setText(textRes)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        setBackgroundResource(tv.resourceId)
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        setPadding(12.dp(), 0, 12.dp(), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 32.dp())
    }

    // ---- Custom Tasks Logic ----

    private fun addNewTask() {
        val newTask = CustomTask(java.util.UUID.randomUUID().toString())
        customTasks.add(newTask)
        saveAndRefresh()
    }

    private fun saveAndRefresh() {
        saveCustomTasks(customTasks, sharedPref)
        refreshTaskList()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshTaskList() {
        llCustomTasks.removeAllViews()
        tvEmpty.visibility = if (customTasks.isEmpty()) View.VISIBLE else View.GONE

        val selectedCount = customTasks.count { it.isSelected }
        cvBatchActions.visibility = if (customTasks.isNotEmpty()) View.VISIBLE else View.GONE
        btnBatchExecute.apply {
            text = getString(R.string.wanxiang_batch_execute) + " ($selectedCount)"
            isClickable = selectedCount > 0 && !isDownloading
            alpha = if (selectedCount > 0 && !isDownloading) 1f else 0.5f
            setOnClickListener { if (selectedCount > 0 && !isDownloading) executeBatch() }
        }
        btnBatchDelete.apply {
            text = getString(R.string.wanxiang_batch_delete) + " ($selectedCount)"
            isClickable = selectedCount > 0
            alpha = if (selectedCount > 0) 1f else 0.5f
            setOnClickListener { if (selectedCount > 0) executeBatchDelete() }
        }

        for ((index, task) in customTasks.withIndex()) {
            llCustomTasks.addView(createTaskCard(index, task))
        }
    }

    private fun createTaskCard(index: Int, task: CustomTask): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val card = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 12.dp() }
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            val borderColor = if (task.isSelected) color(R.color.blue) else color(R.color.surface0)
            background = GradientDrawable().apply {
                setStroke(1.dp(), borderColor)
                cornerRadius = 4.dp().toFloat()
            }
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val checkBox = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { marginEnd = 12.dp() }
            val accent = accentColor(ctx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * ctx.resources.displayMetrics.density)
                if (task.isSelected) {
                    setColor(accent)
                } else {
                    setColor(Color.argb(0x18, Color.red(accent), Color.green(accent), Color.blue(accent)))
                    setStroke(2.dp(), accent)
                }
            }
            text = if (task.isSelected) "✓" else ""
            setTextColor(if (task.isSelected) color(android.R.color.white) else accent)
            setOnClickListener {
                customTasks[index] = customTasks[index].copy(isSelected = !customTasks[index].isSelected)
                saveAndRefresh()
            }
        }
        header.addView(checkBox)

        val titleView = TextView(ctx).apply {
            text = task.name.ifBlank { getString(R.string.wanxiang_default_name) }
            textSize = 14f
            setTextColor(if (task.isSelected) color(R.color.text) else color(R.color.subtext0))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggleView = TextView(ctx).apply {
            text = if (task.isExpanded) "▲" else "▼"
            textSize = 12f
            setTextColor(color(R.color.surface2))
        }
        header.addView(titleView)
        header.addView(toggleView)

        header.setOnClickListener {
            customTasks[index] = customTasks[index].copy(isExpanded = !customTasks[index].isExpanded)
            saveAndRefresh()
        }

        card.addView(header)

        if (task.isExpanded) {
            card.addView(createExpandedContent(index, task))
        }

        return card
    }

    private fun createExpandedContent(index: Int, task: CustomTask): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 8.dp() }
        }

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = 8.dp() }
            setBackgroundColor(listDividerColor(ctx))
        }
        content.addView(divider)

        content.addView(makeLabel(ctx, getString(R.string.wanxiang_task_alias)))
        val nameInput = makeEditText(ctx, task.name, hint = getString(R.string.wanxiang_hint_task_name)) { text ->
            customTasks[index] = customTasks[index].copy(name = text)
        }
        content.addView(nameInput)

        content.addView(makeLabel(ctx, getString(R.string.wanxiang_url_or_path)))
        val urlInput = makeEditText(ctx, task.url, hint = getString(R.string.wanxiang_hint_url)) { text ->
            customTasks[index] = customTasks[index].copy(url = text)
        }
        content.addView(urlInput)

        content.addView(makeLabel(ctx, getString(R.string.wanxiang_target_path)))
        val pathLabel = if (task.boundPath == "DEFAULT") {
            DataManager.userDataDir.absolutePath + " (Default)"
        } else {
            getString(R.string.wanxiang_auth_prefix) + Uri.decode(task.boundPath).substringAfterLast(":")
        }
        val pathRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val pathView = EditText(ctx).apply {
            setText(pathLabel)
            isEnabled = false
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, 38.dp(), 1f)
            background = GradientDrawable().apply {
                setStroke(1.dp(), listDividerColor(ctx))
                cornerRadius = 4.dp().toFloat()
            }
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
        pathRow.addView(pathView)

        val configBtn = Button(ctx).apply {
            text = getString(R.string.wanxiang_configure_path)
            textSize = 12f
            setBackgroundColor(colorPrimaryColor(ctx))
            setTextColor(color(android.R.color.white))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                38.dp(),
            ).apply { marginStart = 8.dp() }
            setOnClickListener {
                pendingPathTaskId = task.id
                dirPickerLauncher.launch(null)
            }
        }
        pathRow.addView(configBtn)
        content.addView(pathRow)

        val decompressRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 10.dp() }
        }
        val decompressLabel = TextView(ctx).apply {
            text = getString(R.string.wanxiang_must_decompress)
            textSize = 13f
            setTextColor(color(R.color.text))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        decompressRow.addView(decompressLabel)
        val decompressCheck = TextView(ctx).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp()).apply { marginStart = 8.dp() }
            val accent = accentColor(ctx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * ctx.resources.displayMetrics.density)
                if (task.needsDecompress) {
                    setColor(accent)
                } else {
                    setColor(Color.argb(0x18, Color.red(accent), Color.green(accent), Color.blue(accent)))
                    setStroke(2.dp(), accent)
                }
            }
            text = if (task.needsDecompress) "✓" else ""
            setTextColor(if (task.needsDecompress) color(android.R.color.white) else accent)
            setOnClickListener {
                customTasks[index] = customTasks[index].copy(needsDecompress = !customTasks[index].needsDecompress)
                saveAndRefresh()
            }
        }
        decompressRow.addView(decompressCheck)
        content.addView(decompressRow)

        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 16.dp() }
        }

        val deleteBtn = TextView(ctx).apply {
            text = getString(R.string.wanxiang_delete_task)
            textSize = 12f
            setTextColor(color(R.color.red))
            setPadding(8.dp(), 12.dp(), 8.dp(), 12.dp())
            setOnClickListener {
                customTasks.removeAt(index)
                saveAndRefresh()
            }
        }
        actionRow.addView(deleteBtn)

        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        actionRow.addView(spacer)

        val execBtn = Button(ctx).apply {
            text = getString(R.string.wanxiang_execute_task)
            textSize = 12f
            minHeight = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 34.dp())
            gravity = Gravity.CENTER
            setPadding(12.dp(), 0, 12.dp(), 0)
            setBackgroundColor(accentColor(ctx))
            setTextColor(color(android.R.color.white))
            setOnClickListener {
                val current = customTasks[index]
                if (current.url.isNotBlank() && !isDownloading) {
                    executeSingleTask(current)
                }
            }
            isEnabled = task.url.isNotBlank() && !isDownloading
        }
        actionRow.addView(execBtn)
        content.addView(actionRow)

        return content
    }

    private fun executeBatch() {
        val targets = customTasks.filter { it.isSelected && it.url.isNotBlank() }
        if (targets.isEmpty()) return

        isDownloading = true
        btnBatchExecute.isClickable = false
        btnBatchExecute.alpha = 0.5f
        llCustomProgress.removeAllViews()

        val tasks = targets.map { t ->
            val fName = t.url.substringAfterLast("/")
            TaskState(
                "${t.name.ifBlank { getString(R.string.wanxiang_default_short) }} ($fName)",
                t.url,
                needsDecompress = t.needsDecompress,
            )
        }

        for (task in tasks) {
            addDownloadProgressItem(llCustomProgress, task, requireContext())
        }

        lifecycleScope.launch {
            for ((tData, uiState) in targets.zip(tasks)) {
                DownloadManager.downloadAndDeploy(
                    task = uiState,
                    token = "",
                    context = requireContext(),
                    rules = emptyList(),
                    targetPaths = listOf(tData.boundPath),
                    onProgress = { t ->
                        lifecycleScope.launch(Dispatchers.Main) { updateDownloadProgressItem(llCustomProgress, t, requireContext(), R.color.red) }
                    },
                )
            }
            isDownloading = false
            btnBatchExecute.isClickable = true
            btnBatchExecute.alpha = 1f
        }
    }

    private fun executeBatchDelete() {
        val targets = customTasks.filter { it.isSelected }
        requireContext().confirmDialog(
            title = R.string.wanxiang_batch_delete,
            message = getString(R.string.wanxiang_batch_delete_confirm, targets.size),
            onConfirm = {
                customTasks.removeAll(targets.toSet())
                saveAndRefresh()
            },
        )
    }

    private fun executeSingleTask(task: CustomTask) {
        if (isDownloading) return
        isDownloading = true
        btnBatchExecute.isClickable = false
        btnBatchExecute.alpha = 0.5f
        llCustomProgress.removeAllViews()

        val uiState = TaskState(
            task.name.ifBlank { getString(R.string.wanxiang_default_short) } + " (${task.url.substringAfterLast("/")})",
            task.url,
            needsDecompress = task.needsDecompress,
        )
        addDownloadProgressItem(llCustomProgress, uiState, requireContext())

        lifecycleScope.launch {
            DownloadManager.downloadAndDeploy(
                task = uiState,
                token = "",
                context = requireContext(),
                rules = emptyList(),
                targetPaths = listOf(task.boundPath),
                onProgress = { t ->
                    lifecycleScope.launch(Dispatchers.Main) { updateDownloadProgressItem(llCustomProgress, t, requireContext(), R.color.red) }
                },
            )
            isDownloading = false
            btnBatchExecute.isClickable = true
            btnBatchExecute.alpha = 1f
        }
    }

    private fun makeLabel(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 11f
        setTextColor(color(R.color.overlay0))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 4.dp() }
    }

    private fun makeEditText(
        ctx: Context,
        value: String,
        hint: String,
        onChanged: (String) -> Unit,
    ): View {
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val box = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 38.dp()).apply { bottomMargin = 10.dp() }
            background = GradientDrawable().apply {
                setStroke(1.dp(), listDividerColor(ctx))
                cornerRadius = 4.dp().toFloat()
            }
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
        val edit = EditText(ctx).apply {
            setText(value)
            setHint(hint)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onChanged(text.toString())
                    saveCustomTasks(customTasks, sharedPref)
                }
            }
        }
        box.addView(edit)
        return box
    }

    // ---- Utility ----

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

    private fun colorPrimaryColor(ctx: Context): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
        return tv.data
    }

    private fun textAppearance(ctx: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) tv.resourceId else tv.data
    }
}
