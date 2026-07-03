/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ViewAnimator
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardCategory
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class ClipboardUi(override val ctx: Context, private val theme: Theme) : Ui {

    val recyclerView = recyclerView {
        addItemDecoration(SpacesItemDecoration(dp(4)))
    }

    val enableUi = ClipboardInstructionUi.Enable(ctx)

    val emptyUi = ClipboardInstructionUi.Empty(ctx)

    val viewAnimator = view(::ViewAnimator) {
        add(recyclerView, lParams(matchParent, matchParent))
        add(emptyUi.root, lParams(matchParent, matchParent))
        add(enableUi.root, lParams(matchParent, matchParent))
    }

    private val categoryButtons = linkedMapOf(
        ClipboardCategory.All to createCategoryButton(R.string.clipboard_category_all),
        ClipboardCategory.Favorites to createCategoryButton(R.string.clipboard_category_favorites),
        ClipboardCategory.Media to createCategoryButton(R.string.clipboard_category_media),
        ClipboardCategory.Local to createCategoryButton(R.string.clipboard_category_local),
        ClipboardCategory.Remote to createCategoryButton(R.string.clipboard_category_remote)
    )

    override val root = verticalLayout {
        add(horizontalLayout {
            setPaddingDp(4, 8, 4, 4)
            categoryButtons.forEach { (_, button) ->
                add(
                    button,
                    LinearLayout.LayoutParams(0, dp(30), 1f)
                        .apply { marginEnd = dp(4) }
                )
            }
        }, lParams(matchParent, wrapContent))
        add(viewAnimator, LinearLayout.LayoutParams(matchParent, 0, 1f))
    }

    private fun createCategoryButton(textRes: Int) = textView {
        gravity = gravityCenter
        minWidth = dp(64)
        text = ctx.getString(textRes)
        textSize = theme.generalStyle.clipboardCategoryTextSize
        setPaddingDp(12, 0, 12, 0)
        background = categoryBackground(selected = false)
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_category_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
    }

    private fun categoryBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = ctx.dp(15).toFloat()
        setColor(
            if (selected) ColorManager.getColor("clipboard_category_selected_back_color")
            else ColorManager.getColor("clipboard_category_back_color")
        )
    }

    fun setOnCategorySelectedListener(listener: (ClipboardCategory) -> Unit) {
        categoryButtons.forEach { (category, button) ->
            button.setOnClickListener {
                setSelectedCategory(category)
                listener(category)
            }
        }
    }

    fun setSelectedCategory(category: ClipboardCategory) {
        categoryButtons.forEach { (buttonCategory, button) ->
            val selected = buttonCategory == category
            button.background = categoryBackground(selected)
            button.setTextColor(
                if (selected) ColorManager.getColor("clipboard_category_selected_text_color")
                else ColorManager.getColor("key_text_color")
            )
        }
    }

    fun switchUiByState(state: ClipboardStateMachine.State) {
        when (state) {
            ClipboardStateMachine.State.Normal -> {
                viewAnimator.displayedChild = 0
            }

            ClipboardStateMachine.State.AddMore -> {
                viewAnimator.displayedChild = 1
            }

            ClipboardStateMachine.State.EnableListening -> {
                viewAnimator.displayedChild = 2
            }
        }
    }
}
