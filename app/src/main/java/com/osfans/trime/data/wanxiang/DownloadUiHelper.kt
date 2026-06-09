/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.wanxiang

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.TextViewCompat

/**
 * Adds a per-task download progress row (title + status + ProgressBar) to [parent].
 * Returns the created container for manual removal if needed.
 */
fun addDownloadProgressItem(
    parent: LinearLayout,
    task: TaskState,
    context: Context,
    @ColorRes statusColor: Int? = null,
): LinearLayout {
    val dp = context.resources.displayMetrics.density
    fun Int.dp() = (this * dp).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = 8.dp()
            bottomMargin = 4.dp()
        }
    }
    val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    val titleView = TextView(context).apply {
        text = task.title
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        maxLines = 1
    }
    val statusView = TextView(context).apply {
        text = task.status
        textSize = 11f
        if (statusColor != null) setTextColor(ContextCompat.getColor(context, statusColor))
    }
    header.addView(titleView)
    header.addView(statusView)

    val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    container.addView(header)
    container.addView(bar)
    container.tag = task
    parent.addView(container)
    return container
}

/**
 * Updates an existing progress row for [task] inside [parent].
 * @param errorColor color applied to status text when task.isError is true
 */
fun updateDownloadProgressItem(
    parent: LinearLayout,
    task: TaskState,
    context: Context,
    @ColorRes errorColor: Int,
) {
    for (child in parent.children) {
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
            if (task.isError) statusView.setTextColor(ContextCompat.getColor(context, errorColor))
        }
    }
}

// ---- Shared UI constructors used by download-enabled fragments ----

fun themedDivider(context: Context): View = View(context).apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        1,
    ).apply {
        topMargin = 12.dp(context)
        bottomMargin = 4.dp(context)
    }
    setBackgroundColor(themedListDividerColor(context))
}

fun themedSectionLabel(context: Context, text: CharSequence): TextView = TextView(context).apply {
    this.text = text
    TextViewCompat.setTextAppearance(this, themedTextAppearance(context, android.R.attr.textAppearanceListItem))
    typeface = Typeface.DEFAULT_BOLD
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 4.dp(context) }
}

fun themedSecondaryText(context: Context, text: String): TextView = TextView(context).apply {
    this.text = text
    TextViewCompat.setTextAppearance(this, themedTextAppearance(context, android.R.attr.textAppearanceListItemSecondary))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 8.dp(context) }
}

fun themedRowLabel(context: Context, titleRes: Int): TextView = TextView(context).apply {
    setText(titleRes)
    TextViewCompat.setTextAppearance(this, themedTextAppearance(context, android.R.attr.textAppearanceListItem))
    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
}

fun themedColor(context: Context, @ColorRes resId: Int): Int = ContextCompat.getColor(context, resId)

fun themedListDividerColor(context: Context): Int {
    val tv = TypedValue()
    context.theme.resolveAttribute(android.R.attr.listDivider, tv, true)
    return tv.data
}

fun themedAccentColor(context: Context): Int {
    val tv = TypedValue()
    context.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)
    return tv.data
}

fun themedTextAppearance(context: Context, attr: Int): Int {
    val tv = TypedValue()
    context.theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) tv.resourceId else tv.data
}

fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
