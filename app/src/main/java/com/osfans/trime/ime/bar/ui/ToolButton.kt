/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.keyboard.GestureFrame
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
import splitties.views.imageResource
import splitties.views.padding

class ToolButton : GestureFrame {
    private val image = imageView {
        isClickable = false
        isFocusable = false
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
    }

    private val label = textView {
        isClickable = false
        isFocusable = false
        setSingleLine()
        ellipsize = null
    }

    enum class ContentType {
        ICON,
        TEXT,
        LOCAL_IMAGE,
        ICON_ONLY,
    }

    var contentType = ContentType.TEXT
        private set
    private lateinit var config: ToolBar.Button
    private var toggleKey: String? = null

    constructor(context: Context, @DrawableRes icon: Int) : super(context) {
        config = ToolBar.Button()
        setupContent(ContentType.ICON_ONLY, icon = icon)
    }

    constructor(context: Context, config: ToolBar.Button) : super(context) {
        initFromConfig(config)
    }

    fun setIcon(@DrawableRes icon: Int) {
        image.imageResource = icon
    }

    fun setText(text: String) {
        label.text = text
    }

    fun setTextColor(@ColorInt color: Int) {
        label.setTextColor(color)
    }

    fun setIconTint(@ColorInt color: Int?) {
        image.imageTintList = ColorStateList.valueOf(color ?: ColorManager.getColor("candidate_text_color"))
    }

    fun setIconScale(scale: Float) {
        image.scaleX = scale
        image.scaleY = scale
    }

    fun updateStyle() {
        if (needsStyleUpdate()) {
            removeAllViews()
            setupFromConfig()
        }
    }

    private fun needsStyleUpdate(): Boolean = toggleKey != null ||
        config.foreground.style.isNotEmpty() ||
        config.foreground.optionStyles.isNotEmpty()

    private fun setupContent(
        type: ContentType,
        @DrawableRes icon: Int? = null,
        text: String? = null,
        drawable: android.graphics.drawable.Drawable? = null,
    ) {
        val foreground = config.foreground
        contentType = type
        removeAllViews()

        when (type) {
            ContentType.ICON -> text?.let {
                image.imageDrawable = IconicsDrawable(context, it).apply {
                    sizeDp = foreground.fontSize.toInt()
                }
            }

            ContentType.TEXT -> {
                text?.let { label.text = it }
                label.textSize = foreground.fontSize
                label.padding = dp(foreground.padding)
                label.typeface = FontManager.getTypeface("toolbar_font")
                label.fontFeatureSettings = FontManager.fontFeatureSettings
                add(label, lParams(wrapContent, wrapContent, gravityCenter))
            }

            ContentType.LOCAL_IMAGE -> drawable?.let { image.setImageDrawable(it) }

            ContentType.ICON_ONLY -> icon?.let { image.imageResource = it }
        }

        if (type != ContentType.TEXT) {
            image.padding = dp(foreground.padding)
            add(image, lParams(wrapContent, wrapContent, gravityCenter))
        }

        applyColors()
    }

    private fun setupMultiIconContent(segments: List<LabelSegment>) {
        val foreground = config.foreground
        contentType = ContentType.ICON
        removeAllViews()

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
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                    )
                }

                is LabelSegment.Text -> {
                    val tv = textView {
                        text = seg.content
                        textSize = foreground.fontSize
                        padding = dp(foreground.padding)
                        typeface = FontManager.getTypeface("toolbar_font")
                        fontFeatureSettings = FontManager.fontFeatureSettings
                    }
                    hLayout.addView(
                        tv,
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                    )
                }
            }
        }
        add(hLayout, lParams(wrapContent, wrapContent, gravityCenter))

        applyMultiIconColors()
    }

    private fun applyMultiIconColors() {
        val foreground = config.foreground
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
            val child = container.getChildAt(i)
            when (child) {
                is ImageView -> child.imageTintList = colorStateList
                is android.widget.TextView -> child.setTextColor(colorStateList)
            }
        }
    }

    private fun applyColors() {
        val foreground = config.foreground
        val normalColor = foreground.normal.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("candidate_text_color")

        val highlightColor = foreground.highlight.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("hilited_candidate_text_color")
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(highlightColor, normalColor),
        )

        when (contentType) {
            ContentType.ICON -> image.imageTintList = colorStateList
            ContentType.TEXT -> label.setTextColor(colorStateList)
            ContentType.LOCAL_IMAGE -> image.imageTintList = null
            ContentType.ICON_ONLY -> image.imageTintList = ColorStateList.valueOf(normalColor)
        }
    }

    private fun initFromConfig(config: ToolBar.Button) {
        this.config = config
        val keyAction = KeyActionManager.getAction(config.action)
        val toggle = keyAction.toggle

        if (toggle.isNotEmpty() && config.foreground.optionStyles.size == 2) {
            this.toggleKey = toggle
        }

        isRepeatable = keyAction.isRepeatable
        setupFromConfig()
    }

    private fun getActiveStyle(): String {
        val optionStyles = config.foreground.optionStyles
        if (toggleKey != null && optionStyles.size == 2) {
            val rime = RimeDaemon.getFirstSessionOrNull()!!
            val toggleOn = rime.run { getRuntimeOption(toggleKey!!) }
            return optionStyles[if (toggleOn) 1 else 0]
        }
        return config.foreground.style
    }

    private fun setupFromConfig() {
        val style = getActiveStyle()

        when {
            style.matches(IMAGE_PATTERN) -> {
                ColorManager.getDrawable(style)?.let { setupContent(ContentType.LOCAL_IMAGE, drawable = it) }
                    ?: setupFallbackContent(config)
            }

            style.isNotEmpty() -> {
                if (style.isIconFont) {
                    val segments = style.parseLabelSegments()
                    if (segments.size == 1 && segments.first() is LabelSegment.Icon) {
                        setupContent(ContentType.ICON, text = (segments.first() as LabelSegment.Icon).cmdName)
                    } else {
                        setupMultiIconContent(segments)
                    }
                } else {
                    setupContent(ContentType.TEXT, text = style)
                }
            }

            else -> setupFallbackContent(config)
        }

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

    private fun setupFallbackContent(config: ToolBar.Button) {
        val action = KeyActionManager.getAction(config.action).getLabel(KeyboardWindow.currentKeyboard)
        setupContent(ContentType.TEXT, text = action)
    }

    companion object {
        private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()

        fun getContentType(style: String): ContentType = when {
            style.isEmpty() -> ContentType.TEXT
            style.matches(IMAGE_PATTERN) -> ContentType.LOCAL_IMAGE
            style.isIconFont -> ContentType.ICON
            else -> ContentType.TEXT
        }
    }
}
