/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.nav

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.keyboard.LabelSegment
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.parseLabelSegments
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageDrawable
import splitties.views.padding

class NavBarButton(
    context: Context,
    config: ToolBar.Button,
    onClick: (View) -> Unit,
) : FrameLayout(context) {

    init {
        setupBackground(config)
        setupContent(config)
        setOnClickListener { onClick(this) }
        isClickable = true
        isFocusable = true
    }

    private fun setupBackground(config: ToolBar.Button) {
        val bg = config.background
        val normalColor = bg.normal.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor) ?: 0
        val highlightColor = bg.highlight.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor)
            ?: ColorManager.getColor("hilited_candidate_button_color")

        val vInset = dp(bg.verticalInset)
        val hInset = dp(bg.horizontalInset)
        background = StateListDrawable().apply {
            listOf(
                highlightColor to intArrayOf(android.R.attr.state_pressed),
                normalColor to intArrayOf(),
            ).forEach { (color, state) ->
                val shape = when (bg.type) {
                    "rectangle" -> GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(bg.cornerRadius.toInt()).toFloat()
                    }
                    "circle" -> ShapeDrawable(OvalShape()).apply { paint.color = color }
                    else -> return@forEach
                }
                addState(state, LayerDrawable(arrayOf(shape)).apply { setLayerInset(0, hInset, vInset, hInset, vInset) })
            }
        }
    }

    private fun setupContent(config: ToolBar.Button) {
        val foreground = config.foreground
        val style = foreground.style.ifEmpty {
            KeyActionManager.getAction(config.action)
                .getLabel(KeyboardWindow.currentKeyboard)
        }

        when {
            style.matches(IMAGE_PATTERN) -> {
                ColorManager.getDrawable(style)?.let { drawable ->
                    val image = imageView {
                        imageDrawable = drawable
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        isClickable = false
                        isFocusable = false
                        padding = dp(foreground.padding)
                    }
                    add(image, lParams(wrapContent, wrapContent, gravityCenter))
                }
            }
            style.isNotEmpty() -> {
                if (style.isIconFont) {
                    val segments = style.parseLabelSegments()
                    if (segments.size == 1 && segments.first() is LabelSegment.Icon) {
                        addSingleIcon(foreground, (segments.first() as LabelSegment.Icon).cmdName)
                    } else {
                        addMultiIconContent(foreground, segments)
                    }
                } else {
                    val label = textView {
                        text = style
                        textSize = foreground.fontSize
                        padding = dp(foreground.padding)
                        typeface = FontManager.getTypeface("unrolled_nav_bar_font")
                        fontFeatureSettings = FontManager.fontFeatureSettings
                        isClickable = false
                        isFocusable = false
                        setSingleLine()
                        ellipsize = null
                    }
                    add(label, lParams(wrapContent, wrapContent, gravityCenter))
                }
            }
        }
        applyColors(foreground)
    }

    private fun addSingleIcon(foreground: ToolBar.Button.Foreground, iconName: String) {
        val image = imageView {
            isClickable = false
            isFocusable = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            padding = dp(foreground.padding)
            imageDrawable = IconicsDrawable(context, iconName).apply {
                sizeDp = foreground.fontSize.toInt()
            }
        }
        add(image, lParams(wrapContent, wrapContent, gravityCenter))
    }

    private fun addMultiIconContent(foreground: ToolBar.Button.Foreground, segments: List<LabelSegment>) {
        val hLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = gravityCenter
        }
        segments.forEach { seg ->
            when (seg) {
                is LabelSegment.Icon -> {
                    val iv = ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        isClickable = false
                        isFocusable = false
                        imageDrawable = IconicsDrawable(context, seg.cmdName).apply {
                            sizeDp = foreground.fontSize.toInt()
                        }
                        padding = dp(foreground.padding)
                    }
                    hLayout.addView(
                        iv,
                        ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                    )
                }
                is LabelSegment.Text -> {
                    val tv = textView {
                        text = seg.content
                        textSize = foreground.fontSize
                        padding = dp(foreground.padding)
                        typeface = FontManager.getTypeface("unrolled_nav_bar_font")
                        fontFeatureSettings = FontManager.fontFeatureSettings
                    }
                    hLayout.addView(
                        tv,
                        ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                    )
                }
            }
        }
        add(hLayout, lParams(wrapContent, wrapContent, gravityCenter))
        applyMultiIconColors(foreground)
    }

    private fun applyMultiIconColors(foreground: ToolBar.Button.Foreground) {
        val normalColor = foreground.normal.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("candidate_text_color")
        val highlightColor = foreground.highlight.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("hilited_candidate_text_color")
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(highlightColor, normalColor),
        )
        val container = getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until container.childCount) {
            when (val child = container.getChildAt(i)) {
                is ImageView -> child.imageTintList = colorStateList
                is android.widget.TextView -> child.setTextColor(colorStateList)
            }
        }
    }

    private fun applyColors(foreground: ToolBar.Button.Foreground) {
        val normalColor = foreground.normal.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("candidate_text_color")
        val highlightColor = foreground.highlight.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("hilited_candidate_text_color")
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(highlightColor, normalColor),
        )
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is ImageView -> child.imageTintList = colorStateList
                is android.widget.TextView -> child.setTextColor(colorStateList)
            }
        }
    }

    companion object {
        private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()
    }
}
