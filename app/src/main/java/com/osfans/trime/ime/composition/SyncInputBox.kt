/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * An editable-looking mirror of the target editor. The text is owned by the
 * target editor: tapping moves its cursor, while long-pressing and dragging
 * selects text there. Regular typing still goes through the IME's normal
 * target input path, so undo, cut/copy/paste and input-type restrictions keep
 * working as usual.
 */
class SyncInputBox(
    context: Context,
    theme: Theme,
) : ScrollView(context) {
    private var displayData: SyncInputDisplay? = null

    private val mirrorTextView = MirrorTextView(context)
    private val textColor = ColorManager.getColor("text_color")
    private val syncInput = theme.syncInput
    private val verticalPaddingPx: Int

    private val maxHeightPx: Int
        get() {
            val lineHeight = lineHeightPx
            val oneLineHeight = lineHeight + verticalPaddingPx * 2
            val configured = syncInput.maxHeight?.takeIf { it > 0 }?.let { dp(it) }
            return (configured ?: (lineHeight * 3 + verticalPaddingPx * 2)).coerceAtLeast(oneLineHeight)
        }

    private val lineHeightPx: Int
        get() {
            val measured = mirrorTextView.lineHeight
            return if (measured > 0) measured else (mirrorTextView.paint.fontMetrics.bottom - mirrorTextView.paint.fontMetrics.top).toInt()
        }

    var onMoveCursor: ((Int) -> Unit)? = null
    var onSelectRange: ((Int, Int) -> Unit)? = null

    init {
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        isFocusable = false
        isFocusableInTouchMode = false
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND

        val backgroundColor = ColorManager.getColor("text_back_color")
        val alpha = ((syncInput.alpha ?: theme.preedit.alpha).coerceIn(0f, 1f) * 255).toInt()
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp((syncInput.cornerRadius ?: 8f).coerceAtLeast(0f))
                setColor(ColorUtils.setAlphaComponent(backgroundColor, alpha))
            }

        val horizontalPadding = dp((syncInput.horizontalPadding ?: theme.preedit.horizontalPadding).coerceAtLeast(0))
        verticalPaddingPx = dp((syncInput.verticalPadding ?: 6).coerceAtLeast(0))
        val verticalPadding = verticalPaddingPx
        mirrorTextView.apply {
            setTextColor(textColor)
            textSize = (syncInput.fontSize ?: theme.preedit.foreground.fontSize).coerceAtLeast(1f)
            typeface = FontManager.getTypeface()
            gravity = Gravity.TOP or Gravity.START
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setTextIsSelectable(false)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            onTap = { displayOffset ->
                this@SyncInputBox.displayData?.let { current ->
                    current.displayOffsetToDocument(displayOffset)?.let { documentOffset ->
                        val displayOffsetInWindow =
                            current.documentOffsetToDisplay(documentOffset) ?: displayOffset
                        setMirrorCaret(displayOffsetInWindow)
                        onMoveCursor?.invoke(documentOffset)
                    }
                }
            }
            onSelection = { start, end ->
                this@SyncInputBox.displayData?.let { current ->
                    val documentStart = current.displayOffsetToDocument(start)
                    val documentEnd = current.displayOffsetToDocument(end)
                    if (documentStart != null && documentEnd != null) {
                        onSelectRange?.invoke(min(documentStart, documentEnd), max(documentStart, documentEnd))
                    }
                }
            }
        }

        addView(
            mirrorTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun update(display: SyncInputDisplay) {
        this.displayData = display
        if (mirrorTextView.text?.toString() != display.text) {
            mirrorTextView.setText(display.text)
        }
        mirrorTextView.isInteractive = display.editable
        if (mirrorTextView.isSelecting) {
            mirrorTextView.clampSelection()
        } else {
            mirrorTextView.caretOffset = display.cursor ?: -1
            mirrorTextView.selectionFrom = display.selectionStart ?: -1
            mirrorTextView.selectionTo = display.selectionEnd ?: -1
            mirrorTextView.invalidate()
        }
        requestLayout()
        post { scrollToCaret() }
    }

    fun clear() {
        displayData = null
        mirrorTextView.setText("")
        mirrorTextView.caretOffset = -1
        mirrorTextView.selectionFrom = -1
        mirrorTextView.selectionTo = -1
        scrollTo(0, 0)
    }

    private fun setMirrorCaret(offset: Int) {
        mirrorTextView.caretOffset = offset
        mirrorTextView.selectionFrom = offset
        mirrorTextView.selectionTo = offset
        mirrorTextView.invalidate()
        post { scrollToCaret() }
    }

    private fun scrollToCaret() {
        val caret = mirrorTextView.caretOffset
        if (caret < 0) return
        val layout = mirrorTextView.layout ?: return
        val offset = caret.coerceIn(0, mirrorTextView.length())
        val line = layout.getLineForOffset(offset)
        val top = layout.getLineTop(line) + mirrorTextView.totalPaddingTop
        val bottom = layout.getLineBottom(line) + mirrorTextView.totalPaddingTop
        val viewportHeight = height - paddingTop - paddingBottom
        if (viewportHeight <= 0) return
        if (top < scrollY || bottom > scrollY + viewportHeight) {
            smoothScrollTo(0, (top - viewportHeight / 3).coerceAtLeast(0))
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val maxHeight = maxHeightPx
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val limitedHeight = if (parentHeight > 0) min(maxHeight, parentHeight) else maxHeight
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(limitedHeight, MeasureSpec.AT_MOST),
        )
    }
}

private class MirrorTextView(
    context: Context,
) : TextView(context) {
    var onTap: ((Int) -> Unit)? = null
    var onSelection: ((Int, Int) -> Unit)? = null

    var caretOffset: Int = -1
    var selectionFrom: Int = -1
    var selectionTo: Int = -1

    var isSelecting: Boolean = false
        private set

    var isInteractive: Boolean = false
        set(value) {
            field = value
            if (!value) resetTouchState()
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val parentScrollView: ScrollView? get() = parent as? ScrollView

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isDown = false
    private var isMoved = false
    private var selectionAnchor = 0
    private var selectionFocus = 0

    private val longPressRunnable =
        Runnable {
            if (!isDown || !isInteractive || isMoved) return@Runnable
            isSelecting = true
            selectionAnchor = offsetAt(downX, downY)
            selectionFocus = selectionAnchor
            updateLocalSelection()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

    private val selectionPaint =
        Paint().apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(ColorManager.getColor("hilited_text_color"), 0x40)
        }
    private val caretPaint =
        Paint().apply {
            style = Paint.Style.FILL
            color = ColorManager.getColor("hilited_text_color")
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parentScrollView?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                isDown = true
                isMoved = false
                isSelecting = false
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return false
                val previousY = lastY
                lastX = event.x
                lastY = event.y
                if (isSelecting) {
                    selectionFocus = offsetAt(event.x, event.y)
                    updateLocalSelection()
                    autoScrollWhileSelecting(event.y)
                    return true
                }
                if (!isMoved && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    isMoved = true
                    removeCallbacks(longPressRunnable)
                }
                if (isMoved) {
                    parentScrollView?.scrollBy(0, (previousY - event.y).toInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isSelecting) {
                    onSelection?.invoke(min(selectionAnchor, selectionFocus), max(selectionAnchor, selectionFocus))
                    isSelecting = false
                } else if (!isMoved) {
                    onTap?.invoke(offsetAt(event.x, event.y))
                    performClick()
                }
                parentScrollView?.requestDisallowInterceptTouchEvent(false)
                isDown = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun clampSelection() {
        val length = text?.length ?: 0
        caretOffset = caretOffset.coerceIn(-1, length)
        selectionFrom = selectionFrom.coerceIn(-1, length)
        selectionTo = selectionTo.coerceIn(-1, length)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        val length = text?.length ?: 0
        val start = min(selectionFrom, selectionTo)
        val end = max(selectionFrom, selectionTo)
        if (start in 0 until end && end <= length) {
            drawSelection(canvas, layout, start, end)
        }
        if (caretOffset in 0..length) {
            drawCaret(canvas, layout, caretOffset, length)
        }
    }

    private fun drawSelection(
        canvas: Canvas,
        layout: Layout,
        start: Int,
        end: Int,
    ) {
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)
        for (line in startLine..endLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val from = max(start, lineStart)
            val to = min(end, lineEnd)
            if (from > to) continue
            val left = layout.getPrimaryHorizontal(from)
            val right = if (to >= lineEnd) layout.getLineRight(line) else layout.getPrimaryHorizontal(to)
            val top = layout.getLineTop(line).toFloat() + totalPaddingTop.toFloat()
            val bottom = layout.getLineBottom(line).toFloat() + totalPaddingTop.toFloat()
            canvas.drawRect(
                RectF(
                    min(left, right) + totalPaddingLeft.toFloat(),
                    top,
                    max(left, right) + totalPaddingLeft.toFloat(),
                    bottom,
                ),
                selectionPaint,
            )
        }
    }

    private fun drawCaret(
        canvas: Canvas,
        layout: Layout,
        caret: Int,
        length: Int,
    ) {
        val line =
            if (caret == length && length > 0 && text?.lastOrNull() == '\n') {
                layout.lineCount - 1
            } else {
                layout.getLineForOffset(caret)
            }
        val x = layout.getPrimaryHorizontal(caret) + totalPaddingLeft.toFloat()
        val top = layout.getLineTop(line).toFloat() + totalPaddingTop.toFloat() + dp(2).toFloat()
        val bottom = layout.getLineBottom(line).toFloat() + totalPaddingTop.toFloat() - dp(2).toFloat()
        canvas.drawRect(
            RectF(x - dp(1).toFloat(), top, x + dp(1).toFloat(), bottom),
            caretPaint,
        )
    }

    private fun updateLocalSelection() {
        selectionFrom = min(selectionAnchor, selectionFocus)
        selectionTo = max(selectionAnchor, selectionFocus)
        caretOffset = selectionFocus
        invalidate()
    }

    private fun autoScrollWhileSelecting(y: Float) {
        val scrollView = parentScrollView ?: return
        val threshold = dp(16)
        val step = dp(12)
        when {
            y < threshold -> scrollView.scrollBy(0, -step)
            y > height - threshold -> scrollView.scrollBy(0, step)
        }
        selectionFocus = offsetAt(lastX, lastY)
        updateLocalSelection()
    }

    private fun offsetAt(
        x: Float,
        y: Float,
    ): Int = getOffsetForPosition(x, y).coerceIn(0, text?.length ?: 0)

    private fun resetTouchState() {
        removeCallbacks(longPressRunnable)
        isDown = false
        isMoved = false
        isSelecting = false
        parentScrollView?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
