/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp

class ClipboardEntryUi(ctx: Context, private val theme: Theme) : Ui {
    override val ctx = ctx.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private val borderSpace get() = ctx.dp(3)
    private val checkboxInset get() = ctx.dp(26)

    val preview = imageView {
        visibility = View.GONE
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        minimumHeight = dp(72)
    }

    private val imagePlaceholder = imageView {
        visibility = View.GONE
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        minimumHeight = dp(72)
        setPaddingDp(16, 8, 16, 8)
        imageDrawable = drawable(R.drawable.ic_baseline_image_24)?.apply {
            setTint(ColorManager.getColor("key_symbol_color"))
        }
    }

    val textView = textView {
        minLines = 1
        maxLines = 5
        textSize = theme.generalStyle.fonts.clipboard_size
        includeFontPadding = false
        setPaddingDp(8, 4, 8, 4)
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(ColorManager.getColor("key_text_color"))
        typeface = FontManager.getTypeface("clipboard_font")
        fontFeatureSettings = FontManager.fontFeatureSettings
    }

    val pin = imageView {
        imageDrawable = drawable(R.drawable.ic_baseline_push_pin_24)!!.apply {
            setTint(ColorManager.getColor("key_symbol_color"))
            setAlpha(0.3f)
        }
    }

    val checkbox = imageView {
        visibility = View.GONE
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    val layout = constraintLayout {
        add(
            preview,
            lParams(0, dp(72)) {
                topOfParent(borderSpace)
                startOfParent(borderSpace)
                endOfParent(borderSpace)
            },
        )
        add(
            imagePlaceholder,
            lParams(0, dp(72)) {
                topOfParent(borderSpace)
                startOfParent(borderSpace)
                endOfParent(borderSpace)
            },
        )
        add(
            textView,
            lParams(0, wrapContent) {
                centerVertically()
                startOfParent(borderSpace)
                endOfParent(borderSpace)
            },
        )
        add(
            pin,
            lParams(dp(12), dp(12)) {
                bottomOfParent(dp(2))
                endOfParent(dp(2))
            },
        )
    }

    override val root = GestureFrame(ctx).apply {
        isClickable = true
        minimumHeight = dp(30)
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                ColorManager.getDecorDrawable(
                    "hilited_clipboard_entry_back_color",
                    "key_border_color",
                    dp(theme.generalStyle.keyBorder),
                    dp(theme.generalStyle.roundCorner),
                ),
            )
            addState(
                intArrayOf(),
                ColorManager.getDecorDrawable(
                    "clipboard_entry_back_color",
                    "key_border_color",
                    dp(theme.generalStyle.keyBorder),
                    dp(theme.generalStyle.roundCorner),
                ),
            )
        }
        add(layout, lParams(matchParent, matchParent))
        add(
            checkbox,
            FrameLayout.LayoutParams(dp(20), dp(20)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                leftMargin = dp(6)
            },
        )
    }

    fun setEntry(text: String, pinned: Boolean, previewBitmap: Bitmap? = null) {
        textView.text = text
        pin.visibility = if (pinned) View.VISIBLE else View.GONE
        if (previewBitmap != null) {
            preview.setImageBitmap(previewBitmap)
            preview.visibility = View.VISIBLE
            imagePlaceholder.visibility = View.GONE
            if (text.isEmpty()) {
                textView.visibility = View.GONE
                root.minimumHeight = ctx.dp(84)
            } else {
                textView.visibility = View.VISIBLE
                textView.maxLines = 2
                textView.setPaddingDp(8, 82, 8, 6)
                root.minimumHeight = ctx.dp(122)
            }
        } else {
            preview.visibility = View.GONE
            if (text.isEmpty()) {
                imagePlaceholder.visibility = View.VISIBLE
                textView.visibility = View.GONE
                root.minimumHeight = ctx.dp(84)
            } else {
                imagePlaceholder.visibility = View.GONE
                textView.visibility = View.VISIBLE
                textView.maxLines = 4
                textView.setPaddingDp(8, 4, 8, 4)
                root.minimumHeight = ctx.dp(30)
            }
        }
    }

    fun setHighlightedEntry(
        text: String,
        query: String,
        pinned: Boolean,
        previewBitmap: Bitmap? = null,
    ) {
        setEntry(text, pinned, previewBitmap)
        if (query.isNotEmpty()) {
            textView.text = buildHighlightedText(text, query)
        }
    }

    fun setMultiSelectMode(inMode: Boolean, selected: Boolean) {
        checkbox.visibility = if (inMode) View.VISIBLE else View.GONE
        (textView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.marginStart = if (inMode) checkboxInset else borderSpace
            textView.requestLayout()
        }
        if (inMode) {
            val color = ColorManager.getColor("clipboard_checkbox_color")
            if (selected) {
                checkbox.imageDrawable = checkbox.drawable(R.drawable.ic_baseline_check_circle_24)?.apply {
                    setTint(color)
                }
            } else {
                val unchecked = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(ctx.dp(2), color)
                    setSize(ctx.dp(20), ctx.dp(20))
                    alpha = 35
                }
                checkbox.imageDrawable = unchecked
            }
        }
    }

    companion object {
        fun buildHighlightedText(
            text: String,
            query: String,
        ): CharSequence {
            val spannable = SpannableString(text)
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            var start = lowerText.indexOf(lowerQuery)
            val highlightColor = ColorManager.getColor("hilited_candidate_text_color")
            while (start >= 0) {
                val end = start + query.length
                spannable.setSpan(
                    ForegroundColorSpan(highlightColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                start = lowerText.indexOf(lowerQuery, end)
            }
            return spannable
        }
    }
}
