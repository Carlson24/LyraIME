/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view

open class PreeditUi(
    final override val ctx: Context,
    private val theme: Theme,
    private val setupPreeditView: (TextView.() -> Unit)? = null,
    private val onMoveCursor: ((Int) -> Unit)? = null,
) : Ui {
    private val textColor = ColorManager.getColor("text_color")
    private val highlightTextColor = ColorManager.getColor("hilited_text_color")

    val preedit =
        view(::PreeditTextView) {
            setTextColor(textColor)
            textSize = theme.preedit.foreground.fontSize
            typeface = FontManager.getTypeface()
            setupPreeditView?.invoke(this)
            onMoveCursor = this@PreeditUi.onMoveCursor
        }

    override val root =
        object : LinearLayout(ctx) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

            init {
                orientation = HORIZONTAL
                add(preedit, lParams())
            }
        }

    var visible = false
        private set

    private fun updateTextView(
        str: CharSequence,
        visible: Boolean,
    ) = preedit.run {
        text = str
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun update(composition: CompositionProto) {
        val preeditText = composition.preedit.orEmpty()
        val hasPreedit = composition.length > 0 && preeditText.isNotEmpty()
        visible = hasPreedit
        if (!hasPreedit) {
            updateTextView("", false)
            return
        }
        val selStart = preeditText.alignToCodePointBoundary(composition.selStart)
        val selEnd = preeditText.alignToCodePointBoundary(composition.selEnd).coerceAtLeast(selStart)
        val string = buildSpannedString {
            append(preeditText)
            if (selEnd > selStart) {
                setSpan(ForegroundColorSpan(highlightTextColor), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        val cursorPos = preeditText.alignToCodePointBoundary(composition.cursorPos)
        val stringWithCursor =
            if (cursorPos == 0 || cursorPos == string.length) {
                string
            } else {
                buildSpannedString {
                    append(string, 0, cursorPos)
                    append(string, cursorPos, string.length)
                }
            }
        updateTextView(stringWithCursor, true)
    }
}

/**
 * Clamps [offset] into this sequence and moves it back to the start of a
 * surrogate pair when it falls between the high and the low surrogate.
 *
 * A style span boundary inside a surrogate pair makes Android lay out each half
 * as a separate run, so both halves render as missing glyphs. That is what made
 * supplementary-plane Han characters (e.g. U+26B99) look like mojibake in the
 * preedit.
 */
internal fun CharSequence.alignToCodePointBoundary(offset: Int): Int {
    val index = offset.coerceIn(0, length)
    if (index == 0 || index == length) return index
    return if (Character.isLowSurrogate(this[index]) &&
        Character.isHighSurrogate(this[index - 1])
    ) {
        index - 1
    } else {
        index
    }
}
