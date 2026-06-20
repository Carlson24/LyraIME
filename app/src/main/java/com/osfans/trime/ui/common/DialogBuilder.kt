/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.common

import android.content.Context
import android.content.DialogInterface
import android.view.ContextThemeWrapper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin
import splitties.views.gravityHorizontalCenter

// ═══════════════════════════════════════════════════════════════
// Foundation: themed dialog builder
// ═══════════════════════════════════════════════════════════════

/**
 * Creates an [AlertDialog.Builder] with the app's unified dialog theme applied.
 * Use this instead of [AlertDialog.Builder] directly everywhere.
 */
fun Context.buildDialog(
    @StringRes title: Int? = null,
): AlertDialog.Builder {
    val themedContext = ContextThemeWrapper(this, R.style.Theme_DialogTheme)
    return AlertDialog.Builder(themedContext).apply {
        if (title != null) setTitle(title)
    }
}

// ═══════════════════════════════════════════════════════════════
// Single-choice picker (replaces ColorPicker/ThemePicker/SoundEffect/EnabledSchema)
// ═══════════════════════════════════════════════════════════════

/**
 * Builds a single-choice list dialog.
 *
 * @param scope Coroutine scope for the selection callback (usually [LifecycleCoroutineScope]).
 * @param title Dialog title string resource.
 * @param items Array of option labels to display.
 * @param selectedIndex Index of the currently selected item, or -1 if none.
 * @param onSelect Suspending callback invoked when an item is selected. Receives the index.
 * @param builderBlock Optional block to further customize the [AlertDialog.Builder].
 */
fun Context.pickSingle(
    scope: LifecycleCoroutineScope,
    @StringRes title: Int,
    items: Array<String>,
    selectedIndex: Int = -1,
    @StringRes emptyMessage: Int = R.string.no_items,
    onSelect: suspend (index: Int) -> Unit,
    builderBlock: (AlertDialog.Builder.() -> Unit)? = null,
): AlertDialog = buildDialog(title)
    .apply {
        if (items.isEmpty()) {
            setMessage(emptyMessage)
        } else {
            setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                scope.launch {
                    onSelect(which)
                    dialog.dismiss()
                }
            }
        }
        setNegativeButton(android.R.string.cancel, null)
        builderBlock?.invoke(this)
    }
    .create()

// ═══════════════════════════════════════════════════════════════
// Multi-choice picker
// ═══════════════════════════════════════════════════════════════

/**
 * Creates a multi-choice list dialog builder.
 *
 * @param title Dialog title string resource.
 * @param items Array of option labels.
 * @param checked Initial checked states.
 * @param onItemToggle Callback when an item's checked state changes.
 */
fun Context.pickMultiple(
    @StringRes title: Int,
    items: Array<String>,
    checked: BooleanArray,
    onItemToggle: ((which: Int, isChecked: Boolean) -> Unit)? = null,
): AlertDialog.Builder = buildDialog(title)
    .apply {
        setMultiChoiceItems(items, checked) { _, which, isChecked ->
            checked[which] = isChecked
            onItemToggle?.invoke(which, isChecked)
        }
    }

// ═══════════════════════════════════════════════════════════════
// Confirm dialog
// ═══════════════════════════════════════════════════════════════

/**
 * Shows a simple confirm dialog.
 */
fun Context.confirmDialog(
    @StringRes title: Int,
    @StringRes message: Int? = null,
    @StringRes positiveText: Int = android.R.string.ok,
    @StringRes negativeText: Int = android.R.string.cancel,
    onConfirm: () -> Unit,
): AlertDialog = buildDialog(title)
    .apply {
        if (message != null) setMessage(message)
        setPositiveButton(positiveText) { _, _ -> onConfirm() }
        setNegativeButton(negativeText, null)
    }
    .show()

/**
 * Shows a simple confirm dialog with a [CharSequence] message.
 */
fun Context.confirmDialog(
    @StringRes title: Int,
    message: CharSequence,
    @StringRes positiveText: Int = android.R.string.ok,
    @StringRes negativeText: Int = android.R.string.cancel,
    onConfirm: () -> Unit,
): AlertDialog = buildDialog(title)
    .apply {
        setMessage(message)
        setPositiveButton(positiveText) { _, _ -> onConfirm() }
        setNegativeButton(negativeText, null)
    }
    .show()

/**
 * Shows a confirm dialog with a custom negative button action.
 */
fun Context.confirmDialog(
    @StringRes title: Int,
    @StringRes message: Int? = null,
    @StringRes positiveText: Int = android.R.string.ok,
    @StringRes negativeText: Int = android.R.string.cancel,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
): AlertDialog = buildDialog(title)
    .apply {
        if (message != null) setMessage(message)
        setPositiveButton(positiveText) { _, _ -> onConfirm() }
        setNegativeButton(negativeText) { _, _ -> onCancel() }
    }
    .show()

// ═══════════════════════════════════════════════════════════════
// Progress dialog
// ═══════════════════════════════════════════════════════════════

/**
 * A managed progress dialog with text and progress bar.
 */
class ProgressDialog(
    private val dialog: AlertDialog,
    private val textView: TextView,
    private val progressBar: ProgressBar,
) {
    var text: CharSequence
        get() = textView.text
        set(value) {
            textView.text = value
        }

    /**
     * Updates the progress bar. Set to -1 for indeterminate.
     */
    var progress: Int
        get() = progressBar.progress
        set(value) {
            if (value < 0) {
                progressBar.isIndeterminate = true
            } else {
                progressBar.isIndeterminate = false
                progressBar.progress = value.coerceIn(0, progressBar.max)
            }
        }

    val isShowing: Boolean get() = dialog.isShowing

    fun show(): ProgressDialog {
        dialog.show()
        return this
    }

    fun setTitle(@StringRes title: Int) {
        dialog.setTitle(title)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        dialog.setOnDismissListener(listener)
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun getButton(which: Int): android.widget.Button = dialog.getButton(which)
}

/**
 * Creates and shows a progress dialog.
 */
fun Context.progressDialog(
    @StringRes title: Int = R.string.loading,
    message: CharSequence? = null,
    max: Int = 100,
    indeterminate: Boolean = true,
    cancelable: Boolean = false,
    onCancel: (() -> Unit)? = null,
): ProgressDialog {
    val dp = dp(16)
    val ctx = this
    val textView = TextView(ctx)
    val progressBar = ProgressBar(ctx).apply {
        this.max = max
        isIndeterminate = indeterminate
    }

    val view = verticalLayout {
        gravity = gravityHorizontalCenter
        if (message != null) {
            add(
                textView.apply { text = message },
                lParams {
                    horizontalMargin = dp
                    verticalMargin = dp
                },
            )
        }
        add(
            progressBar,
            lParams {
                width = matchParent
                horizontalMargin = dp
                verticalMargin = if (message != null) dp(8) else dp(20)
                bottomMargin = dp
            },
        )
    }

    val dialog = buildDialog(title)
        .setView(view)
        .setCancelable(cancelable)
        .apply {
            if (cancelable && onCancel != null) {
                setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            }
        }
        .create()

    return ProgressDialog(
        dialog = dialog,
        textView = textView,
        progressBar = progressBar,
    )
}

// ═══════════════════════════════════════════════════════════════
// See ProgressBarDialogIndeterminate.kt for legacy progress bar tools
// ═══════════════════════════════════════════════════════════════
