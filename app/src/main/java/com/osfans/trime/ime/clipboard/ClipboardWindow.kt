/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewAnimator
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardCategory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.time.Duration.Companion.milliseconds

class ClipboardWindow(
    private val initialCategory: ClipboardCategory? = null,
) : BoardWindow.BarBoardWindow() {

    companion object {
        private const val LAST_OPENED_CATEGORY_KEY = "clipboard_last_opened_category"
        private const val UNDO_TIMEOUT_MS = 5000L
    }

    private val service: TrimeInputMethodService by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val theme: Theme by di.instance()

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val clipboardEnabledPref = prefs.clipboardListening
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste
    private val clipboardMaskSensitive by prefs.clipboardMaskSensitive

    private var currentCategory = initialCategory ?: ClipboardCategory.All
    private var adapterSubmitJob: Job? = null

    private fun resolveInitialCategory(category: ClipboardCategory?): ClipboardCategory {
        category?.let { return it }
        val saved = sharedPreferences.getString(LAST_OPENED_CATEGORY_KEY, ClipboardCategory.All.name).orEmpty()
        return ClipboardCategory.entries.firstOrNull { it.name == saved } ?: ClipboardCategory.All
    }

    private val adapter by lazy {
        object : ClipboardAdapter(theme, clipboardMaskSensitive) {
            init {
                onLongPressEnterSelectMode = { id -> enterMultiSelectMode(id) }
                onSelectionChanged = { updateMultiSelectToolbar() }
            }
            override fun onPaste(entry: DatabaseBean) {
                if (entry.isUriEntry() && entry.type.startsWith("image/")) {
                    val uri = entry.text.toUri()
                    val mimeType = entry.type.takeIf { it.isNotBlank() }
                        ?: context.contentResolver.getType(uri)
                        ?: "image/*"
                    service.commitImage(uri, mimeType)
                } else {
                    service.commitText(entry.text)
                }
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onPin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.unpin(id) }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id)
            }

            override fun onShare(entry: DatabaseBean) {
                val target = if (entry.isUriEntry()) {
                    val uri = entry.text.toUri()
                    val mimeType = runCatching { context.contentResolver.getType(uri) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: entry.type.takeIf {
                            it.isNotBlank() && it != ClipDescription.MIMETYPE_TEXT_URILIST
                        }
                        ?: "*/*"
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        clipData = ClipData.newUri(context.contentResolver, "clipboard", uri)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entry.text)
                    }
                }
                val chooser = Intent.createChooser(target, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(chooser)
            }

            override fun onSplitText(text: String) {
                windowManager.attachWindow(SegmentsWindow(text))
            }

            override fun onSearch(query: String) {
                val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    service.startActivity(webSearchIntent)
                }
            }

            override fun onDial(number: String) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.fromParts("tel", number, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    service.startActivity(intent)
                }
            }

            override fun onOpenLink(uri: Uri) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    service.startActivity(intent)
                }
            }

            override fun onViewImage(uri: Uri) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    service.startActivity(intent)
                }
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch {
                    ClipboardHelper.delete(id)
                    showDeleteToast(id)
                }
            }
        }
    }

    private fun entriesPager(category: ClipboardCategory) = Pager(
        PagingConfig(
            pageSize = 16,
            enablePlaceholders = false,
        ),
    ) {
        ClipboardHelper.entriesPager(category)
    }

    private fun submitCategory(category: ClipboardCategory) {
        currentCategory = category
        sharedPreferences.edit { putString(LAST_OPENED_CATEGORY_KEY, category.name) }
        adapterSubmitJob?.cancel()
        adapterSubmitJob = service.lifecycleScope.launch {
            entriesPager(category).flow.collect {
                adapter.submitData(it)
            }
        }
        ui.setSelectedCategory(category)
    }

    private val ui by lazy {
        ClipboardUi(context, theme).apply {
            recyclerView.apply {
                layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                itemAnimator = null
                adapter = this@ClipboardWindow.adapter
            }
            setSelectedCategory(currentCategory)
            setOnCategorySelectedListener(::submitCategory)
            ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int = makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val entry = adapter.getEntryAt(viewHolder.bindingAdapterPosition) ?: return
                    service.lifecycleScope.launch {
                        ClipboardHelper.delete(entry.id)
                        showDeleteToast(entry.id)
                    }
                }
            }).attachToRecyclerView(recyclerView)
            enableUi.enableButton.setOnClickListener {
                clipboardEnabledPref.setValue(true)
            }
        }
    }

    override fun onCreateView(): View {
        if (initialCategory == null) {
            currentCategory = resolveInitialCategory(null)
        }
        return ui.root
    }

    private val pendingDeleteIds = arrayListOf<Int>()
    private var undoTimeoutJob: Job? = null

    private fun showDeleteToast(vararg ids: Int) {
        undoTimeoutJob?.cancel()
        if (pendingDeleteIds.isNotEmpty()) {
            service.lifecycleScope.launch { ClipboardHelper.realDelete() }
            pendingDeleteIds.clear()
        }
        pendingDeleteIds.addAll(ids.toList())
        val count = pendingDeleteIds.size
        Toast.makeText(context, context.getString(R.string.num_items_deleted, count), Toast.LENGTH_SHORT).show()
        undoTimeoutJob = service.lifecycleScope.launch {
            delay(UNDO_TIMEOUT_MS.milliseconds)
            if (pendingDeleteIds.isNotEmpty()) {
                ClipboardHelper.realDelete()
                pendingDeleteIds.clear()
            }
            undoTimeoutJob = null
            updateUndoButtonVisibility()
        }
        updateUndoButtonVisibility()
    }

    private fun undoLastDelete() {
        undoTimeoutJob?.cancel()
        undoTimeoutJob = null
        if (pendingDeleteIds.isNotEmpty()) {
            service.lifecycleScope.launch {
                ClipboardHelper.undoDelete(*pendingDeleteIds.toIntArray())
                pendingDeleteIds.clear()
                updateUndoButtonVisibility()
            }
        }
    }

    private val barDeleteAllButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_delete_sweep_24)
        imageTintList = android.content.res.ColorStateList.valueOf(ColorManager.getColor("key_text_color"))
        background = null
        setOnClickListener { showDeleteAllConfirm() }
    }

    private val barUsageText = TextView(context).apply {
        gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        textSize = theme.generalStyle.fonts.clipboard_category_size
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
    }

    private val barSelectButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_check_circle_24)
        imageTintList = android.content.res.ColorStateList.valueOf(ColorManager.getColor("key_text_color"))
        background = null
        setOnClickListener { enterMultiSelectMode() }
    }

    private val barSearchButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_search_24)
        imageTintList = android.content.res.ColorStateList.valueOf(ColorManager.getColor("key_text_color"))
        background = null
        setOnClickListener { AppUtils.launchClipSearch(context) }
    }

    private val barUndoButton = TextView(context).apply {
        text = context.getString(R.string.undo)
        gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        textSize = theme.generalStyle.fonts.clipboard_category_size
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
        visibility = View.GONE
        setOnClickListener { undoLastDelete() }
    }

    private val barSelectedCountText = TextView(context).apply {
        gravity = android.view.Gravity.CENTER or android.view.Gravity.CENTER_VERTICAL
        textSize = theme.generalStyle.fonts.clipboard_category_size
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
    }

    private val barCancelSelectButton = TextView(context).apply {
        text = context.getString(R.string.clipboard_exit_select)
        gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        textSize = theme.generalStyle.fonts.clipboard_category_size
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
        setOnClickListener { exitMultiSelectMode() }
    }

    private val barDeleteSelectedButton = TextView(context).apply {
        text = context.getString(R.string.clipboard_delete_selected)
        gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        textSize = theme.generalStyle.fonts.clipboard_category_size
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
        setOnClickListener { showDeleteSelectedConfirm() }
    }

    private fun showDeleteAllConfirm() {
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.ask_to_delete_all)
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    ClipboardHelper.deleteAll(currentCategory, skipPinned = true)
                    ClipboardHelper.realDelete()
                    Toast.makeText(context, context.getString(R.string.delete_all_done), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    private fun enterMultiSelectMode(initialId: Int = -1) {
        adapter.multiSelectMode = true
        if (initialId > 0) {
            adapter.selectedIds.add(initialId)
        }
        adapter.notifyDataSetChanged()
        toolbarAnimator.displayedChild = 1
        updateMultiSelectToolbar()
    }

    private fun exitMultiSelectMode() {
        adapter.multiSelectMode = false
        adapter.selectedIds.clear()
        adapter.notifyDataSetChanged()
        toolbarAnimator.displayedChild = 0
        updateUndoButtonVisibility()
    }

    private fun updateMultiSelectToolbar() {
        val count = adapter.selectedIds.size
        barSelectedCountText.text = context.getString(R.string.clipboard_selected_count, count)
        barDeleteSelectedButton.isEnabled = count > 0
    }

    private fun showDeleteSelectedConfirm() {
        val count = adapter.selectedIds.size
        if (count == 0) return
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(R.string.clipboard_delete_selected)
            .setMessage(context.getString(R.string.clipboard_confirm_delete_selected, count))
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    deleteSelectedEntries()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    private suspend fun deleteSelectedEntries() {
        val ids = adapter.selectedIds.toIntArray()
        if (ids.isEmpty()) return
        undoTimeoutJob?.cancel()
        if (pendingDeleteIds.isNotEmpty()) {
            ClipboardHelper.realDelete()
            pendingDeleteIds.clear()
            updateUndoButtonVisibility()
        }
        ClipboardHelper.markAsDeleted(*ids)
        ClipboardHelper.realDelete()
        adapter.selectedIds.clear()
        exitMultiSelectMode()
        Toast.makeText(context, context.getString(R.string.delete_all_done), Toast.LENGTH_SHORT).show()
    }

    private fun updateUndoButtonVisibility() {
        barUndoButton.visibility =
            if (pendingDeleteIds.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateUsageText() {
        val used = ClipboardHelper.itemCount
        val limit = prefs.clipboardLimit.getValue()
        barUsageText.text = "($used/$limit)"
    }

    private val usageCountListener = ClipboardHelper.OnClipboardUpdateListener {
        barUsageText.post {
            updateUsageText()
        }
    }

    private lateinit var stateMachine: ClipboardStateMachine

    override fun onAttached() {
        updateUsageText()
        val isEmpty = ClipboardHelper.itemCount == 0
        val isListening = clipboardEnabledPref.getValue()
        val initialState = when {
            !isListening -> ClipboardStateMachine.State.EnableListening
            isEmpty -> ClipboardStateMachine.State.AddMore
            else -> ClipboardStateMachine.State.Normal
        }
        stateMachine = ClipboardStateMachine(isEmpty, isListening)
        stateMachine.onStateChanged = { state -> ui.switchUiByState(state) }
        ui.switchUiByState(initialState)
        adapter.addLoadStateListener {
            val empty = it.append.endOfPaginationReached && adapter.itemCount < 1
            stateMachine.updateEmpty(empty)
        }
        submitCategory(currentCategory)
        clipboardEnabledPref.registerOnChangeListener(clipboardEnabledListener)
        updateUndoButtonVisibility()
        ClipboardHelper.addOnUpdateListener(usageCountListener)
    }

    override fun onDetached() {
        ClipboardHelper.removeOnUpdateListener(usageCountListener)
        clipboardEnabledPref.unregisterOnChangeListener(clipboardEnabledListener)
        adapter.onDetached()
        adapterSubmitJob?.cancel()
        undoTimeoutJob?.cancel()
    }

    @Suppress("unused")
    private val clipboardEnabledListener = PreferenceDelegate.OnChangeListener<Boolean> { _, it ->
        stateMachine.updateListening(it)
    }

    override val title: String
        get() = context.getString(R.string.clipboard)

    private lateinit var toolbarAnimator: ViewAnimator

    override fun onCreateBarView(): View {
        val normalBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        normalBar.addView(barUsageText)
        normalBar.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        normalBar.addView(barUndoButton)
        normalBar.addView(barSelectButton)
        normalBar.addView(barSearchButton)
        normalBar.addView(barDeleteAllButton)

        val selectBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }
        selectBar.addView(barSelectedCountText)
        selectBar.addView(barCancelSelectButton)
        selectBar.addView(barDeleteSelectedButton)

        return ViewAnimator(context).apply {
            addView(normalBar)
            addView(selectBar)
            toolbarAnimator = this
        }
    }
}
