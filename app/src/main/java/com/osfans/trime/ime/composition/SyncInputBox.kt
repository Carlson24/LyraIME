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
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.ui.ToolButton
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
 *
 * The box is always one line tall. Overflowing text scrolls horizontally, and
 * the trailing [ToolButton] is configured by `sync_input.button` in the theme.
 */
class SyncInputBox(
    context: Context,
    theme: Theme,
) : FrameLayout(context) {
    private var displayData: SyncInputDisplay? = null

    private val mirrorTextView = MirrorTextView(context)
    private val textScroll = HorizontalScrollView(context)
    private val textColor = ColorManager.getColor("text_color")
    private val syncInput = theme.syncInput
    private val verticalPaddingPx: Int

    private val buttonConfig: ToolBar.Button = syncInput.button ?: DEFAULT_BUTTON
    private val toolButton = ToolButton(context, buttonConfig)
    private val buttonAction: String
        get() = buttonConfig.action.ifEmpty { DEFAULT_ACTION }

    var onAction: ((String) -> Unit)? = null

    private val lineHeightPx: Int
        get() {
            val measured = mirrorTextView.lineHeight
            return if (measured > 0) measured else (mirrorTextView.paint.fontMetrics.bottom - mirrorTextView.paint.fontMetrics.top).toInt()
        }

    private val oneLineHeightPx: Int
        get() = lineHeightPx + verticalPaddingPx * 2

    private val buttonWidthPx: Int
        get() {
            val configured = buttonConfig.size.takeIf { it.size == 2 }?.get(0)
            return if (configured != null && configured >= 0) {
                dp(configured)
            } else {
                oneLineHeightPx.coerceAtLeast(dp(36))
            }
        }

    var onMoveCursor: ((Int) -> Unit)? = null
    var onSelectRange: ((Int, Int) -> Unit)? = null

    init {
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
            maxLines = 1
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

        textScroll.apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = false
            isFocusableInTouchMode = false
            addView(
                mirrorTextView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        toolButton.apply {
            contentDescription = buttonAction
            setOnClickListener { onAction?.invoke(buttonAction) }
            if (buttonConfig.longPressAction.isNotEmpty()) {
                setOnLongClickListener {
                    onAction?.invoke(buttonConfig.longPressAction)
                    true
                }
            }
        }

        addView(
            textScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                marginEnd = buttonWidthPx
            },
        )
        addView(
            toolButton,
            FrameLayout.LayoutParams(
                buttonWidthPx,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
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

    fun updateButtonStyle() {
        toolButton.updateStyle()
    }

    fun clear() {
        displayData = null
        mirrorTextView.setText("")
        mirrorTextView.caretOffset = -1
        mirrorTextView.selectionFrom = -1
        mirrorTextView.selectionTo = -1
        textScroll.scrollTo(0, 0)
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
        val x = layout.getPrimaryHorizontal(offset) + mirrorTextView.totalPaddingLeft
        val viewportWidth = textScroll.width - textScroll.paddingLeft - textScroll.paddingRight
        if (viewportWidth <= 0) return
        val margin = dp(8)
        val current = textScroll.scrollX
        val target =
            when {
                x < current + margin -> (x - margin).toInt()
                x > current + viewportWidth - margin -> (x - viewportWidth + margin).toInt()
                else -> return
            }
        textScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        val minWidth = textScroll.width
        if (minWidth > 0 && mirrorTextView.minWidth != minWidth) {
            mirrorTextView.minWidth = minWidth
            mirrorTextView.requestLayout()
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desiredHeight = oneLineHeightPx
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val limitedHeight = if (parentHeight > 0) min(desiredHeight, parentHeight) else desiredHeight
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(limitedHeight, MeasureSpec.EXACTLY),
        )
    }

    private companion object {
        const val DEFAULT_ACTION = "Return"

        val DEFAULT_BUTTON = ToolBar.Button(
            action = DEFAULT_ACTION,
            foreground = ToolBar.Button.Foreground(
                style = "ic@keyboard_return",
                normal = "text_color",
                highlight = "hilited_text_color",
            ),
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
    private val parentScrollView: HorizontalScrollView? get() = parent as? HorizontalScrollView

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var previousX = 0f
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
                previousX = event.x
                isDown = true
                isMoved = false
                isSelecting = false
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return false
                val previousXPos = previousX
                lastX = event.x
                lastY = event.y
                previousX = event.x
                if (isSelecting) {
                    selectionFocus = offsetAt(event.x, event.y)
                    updateLocalSelection()
                    autoScrollWhileSelecting(event.x)
                    return true
                }
                if (!isMoved && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    isMoved = true
                    removeCallbacks(longPressRunnable)
                }
                if (isMoved) {
                    parentScrollView?.scrollBy((previousXPos - event.x).toInt(), 0)
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

    private fun autoScrollWhileSelecting(x: Float) {
        val scrollView = parentScrollView ?: return
        val threshold = dp(16)
        val step = dp(12)
        val viewportLeft = scrollView.scrollX
        val viewportRight = viewportLeft + scrollView.width
        val delta =
            when {
                x < viewportLeft + threshold -> -step
                x > viewportRight - threshold -> step
                else -> 0
            }
        if (delta != 0) {
            val before = scrollView.scrollX
            scrollView.scrollBy(delta, 0)
            lastX += scrollView.scrollX - before
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
