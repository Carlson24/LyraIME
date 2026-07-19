/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.popup

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.LabelSegment
import com.osfans.trime.ime.keyboard.parseLabelSegments
import com.osfans.trime.util.UnicodeVariantUtils
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class PopupEntryUi(override val ctx: Context, private val theme: Theme, private val keyHeight: Int, radius: Float) : Ui {

    var lastShowTime = -1L

    val textView = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = theme.generalStyle.fonts.popup_size
        gravity = gravityCenter
        setTextColor(ColorManager.getColor("popup_text_color"))
        typeface = FontManager.getTypeface("POPUP_FONT")
        fontFeatureSettings = FontManager.fontFeatureSettings
    }

    val imageView = view(::AppCompatImageView) {
        visibility = android.view.View.GONE
    }

    override val root = constraintLayout {
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(ColorManager.getColor("popup_back_color"))
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(2f)
        add(
            textView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
        add(
            imageView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
    }

    fun setText(text: String) {
        val segments = text.parseLabelSegments()

        root.removeAllViews()

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        if (segments.size == 1) {
            root.addView(
                textView,
                ConstraintLayout.LayoutParams(wrap, keyHeight).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                },
            )
            root.addView(
                imageView,
                ConstraintLayout.LayoutParams(wrap, keyHeight).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                },
            )

            when (val seg = segments.first()) {
                is LabelSegment.Icon -> {
                    imageView.setImageDrawable(
                        IconicsDrawable(ctx, seg.cmdName).apply {
                            sizeDp = theme.generalStyle.fonts.popup_size.toInt()
                            colorFilter = PorterDuffColorFilter(ColorManager.getColor("popup_text_color"), PorterDuff.Mode.SRC_IN)
                        },
                    )
                    imageView.isVisible = true
                    textView.isVisible = false
                }
                is LabelSegment.Text -> {
                    textView.text = UnicodeVariantUtils.toDisplay(seg.content)
                    textView.isVisible = true
                    imageView.isVisible = false
                }
            }
        } else {
            textView.isVisible = false
            imageView.isVisible = false

            val hLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = gravityCenter
            }
            segments.forEach { seg ->
                when (seg) {
                    is LabelSegment.Icon -> {
                        val iv = AppCompatImageView(ctx)
                        iv.setImageDrawable(
                            IconicsDrawable(ctx, seg.cmdName).apply {
                                sizeDp = theme.generalStyle.fonts.popup_size.toInt()
                                colorFilter = PorterDuffColorFilter(ColorManager.getColor("popup_text_color"), PorterDuff.Mode.SRC_IN)
                            },
                        )
                        hLayout.addView(
                            iv,
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                        )
                    }
                    is LabelSegment.Text -> {
                        val tv = AutoScaleTextView(ctx).apply {
                            scaleMode = AutoScaleTextView.Mode.Proportional
                            textSize = theme.generalStyle.fonts.popup_size
                            setTextColor(ColorManager.getColor("popup_text_color"))
                            typeface = FontManager.getTypeface("POPUP_FONT")
                            fontFeatureSettings = FontManager.fontFeatureSettings
                            setText(UnicodeVariantUtils.toDisplay(seg.content))
                        }
                        hLayout.addView(
                            tv,
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                        )
                    }
                }
            }
            root.addView(
                hLayout,
                ConstraintLayout.LayoutParams(wrap, keyHeight).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                },
            )
        }
    }
}
