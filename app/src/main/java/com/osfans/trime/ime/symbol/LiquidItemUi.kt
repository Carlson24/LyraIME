/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.drawable.ColorDrawable
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class LiquidItemUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    private val hlRippleColor =
        runCatching { ColorManager.getColor("hilited_key_back_color") }
            .getOrElse { ColorManager.getColor("hilited_candidate_back_color") }
    private val cornerRadius = ctx.dp(theme.generalStyle.roundCorner)

    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        textSize = theme.generalStyle.fonts.key_size
        typeface = FontManager.getTypeface("key_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
        setPaddingDp(8, 4, 8, 4)
        setTextColor(ColorManager.getColor("key_text_color"))
    }

    override val root = view(::GestureFrame) {
        val content = constraintLayout {
            add(
                mainText,
                lParams(wrapContent, wrapContent) {
                    centerInParent()
                },
            )
        }
        add(content, lParams(matchParent, matchParent))
    }

    fun setActive(active: Boolean) {
        root.background = if (active) {
            roundedRippleDrawable(hlRippleColor, cornerRadius, hlRippleColor)
        } else {
            ColorDrawable(android.graphics.Color.TRANSPARENT)
        }
    }
}