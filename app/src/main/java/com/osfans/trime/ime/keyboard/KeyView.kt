/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.link.AsrkbVoiceHoldSessionController
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class KeyView(
    context: Context,
    private val key: Key,
    private val keyboard: Keyboard,
    private val keyboardView: KeyboardView,
    private val keyboardActionListener: KeyboardActionListener,
) : GestureFrame(context) {

    private val service: TrimeInputMethodService
        get() = keyboardView.service

    private val popup: PopupDelegate
        get() = keyboardView.popup

    private val rime get() = RimeDaemon.getFirstSessionOrNull()!!

    private val hookShiftArrow: Boolean by lazy {
        AppPrefs.defaultInstance().keyboard.hookShiftArrow.getValue()
    }
    private val asrkbAidlVoiceInputEnabled: Boolean
        get() = AppPrefs.defaultInstance().voiceInput.asrkbAidlVoiceInputEnabled.getValue()

    private val deletedTextBuffer = ArrayDeque<String>()

    private var keyPressed = false
    override fun isPressed(): Boolean = keyPressed

    private val asrkbVoiceHoldController by lazy {
        AsrkbVoiceHoldSessionController(
            service = service,
            showOverlay = { keyboardView.showVoiceOverlay() },
            startWave = { keyboardView.startVoiceOverlayWave() },
            updateAmplitude = { amplitude -> keyboardView.updateVoiceOverlayAmplitude(amplitude) },
            hideOverlay = { keyboardView.hideVoiceOverlay() },
            useAidl = { asrkbAidlVoiceInputEnabled },
        )
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var cachedIcon: IconicsDrawable? = null
    private var cachedIconName: String? = null

    private val cachedLocation = intArrayOf(0, 0)
    private val cachedBounds = Rect()
    private var boundsValid = false

    val bounds: Rect
        get() = cachedBounds.also {
            if (!boundsValid) updateBounds()
        }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { getLocationInWindow(it) }
        cachedBounds.set(x + key.extraWidthLeft, y, x + width - key.extraWidthRight, y + height)
        boundsValid = true
    }

    init {
        setWillNotDraw(false)
        isRepeatable = key.click?.isRepeatable ?: false
        isSlideCursor = key.click?.isSlideCursor ?: false
        isSlideDelete = key.click?.isSlideDelete ?: false
        hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK)
        hasDouble = key.hasAction(KeyBehavior.DOUBLE_CLICK)
        hasLazyDouble = key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)
        hasPopup = key.popup.isNotEmpty()

        onPress = {
            if (keyboard.firstPressedKeyIndex == -1) keyboard.firstPressedKeyIndex = id
            setPressedState(true)
            key.getCode(KeyBehavior.CLICK).let { keyboardActionListener.onPress(it) }
            showPopupPreview()
        }

        onRelease = { behavior, isFromLongPress ->
            Timber.d("KeyView release: label=${key.getLabel()}, behavior=$behavior, fromLongPress=$isFromLongPress")
            if (isFromLongPress && isVoiceLongPressAction(key.getAction(KeyBehavior.LONG_CLICK))) {
                asrkbVoiceHoldController.stopIfStarted()
                setPressedState(false)
                dismissPopupPreview()
                if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
            } else if (isFromLongPress) {
                if (hasPopup) {
                    val triggerAction = PopupAction.TriggerAction(id)
                    popup.listener.onPopupAction(triggerAction)
                    triggerAction.outAction?.let { action ->
                        keyboardActionListener.onAction(KeyAction(action))
                        dismissPopupPreview()
                    }
                    setPressedState(false)
                } else if (isRepeatable) {
                    key.getAction(KeyBehavior.CLICK)?.let { processKeyAction(it, KeyBehavior.CLICK) }
                }
            } else {
                when (behavior) {
                    KeyBehavior.CLICK -> {
                        val pressedIdx = keyboard.firstPressedKeyIndex
                        val actionBehavior = if (pressedIdx != -1 && pressedIdx != id) KeyBehavior.COMBO else behavior
                        key.getAction(actionBehavior)?.let { processKeyAction(it, actionBehavior) }
                    }
                    KeyBehavior.DOUBLE_CLICK, KeyBehavior.LAZY_DOUBLE_CLICK,
                    KeyBehavior.SWIPE_UP, KeyBehavior.SWIPE_DOWN, KeyBehavior.SWIPE_LEFT, KeyBehavior.SWIPE_RIGHT,
                    ->
                        key.getAction(behavior)?.let { processKeyAction(it, behavior) }
                    else -> {}
                }

                setPressedState(false)
                dismissPopupPreview()
            }
            if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
        }

        onSwipe = { direction ->
            setPressedState(true)
            showPopupPreview(direction)
        }

        onSlide = { delta, _, _ ->
            if (isSlideCursor) {
                when {
                    delta > 0 -> keyboardActionListener?.onAction(KeyAction("Right"))
                    delta < 0 -> keyboardActionListener?.onAction(KeyAction("Left"))
                }
            } else if (isSlideDelete) {
                val ic = service.currentInputConnection
                when {
                    delta < 0 -> {
                        val beforeText = ic.getTextBeforeCursor(1, 0) ?: ""
                        if (beforeText.isNotEmpty()) {
                            deletedTextBuffer.addFirst(beforeText.toString())
                            ic.deleteSurroundingText(1, 0)
                        }
                    }

                    delta > 0 -> {
                        if (deletedTextBuffer.isNotEmpty()) {
                            ic.commitText(deletedTextBuffer.removeFirst(), 1)
                        }
                    }
                }
            }
        }

        onLongClick = {
            val longPressAction = key.getAction(KeyBehavior.LONG_CLICK)
            if (isVoiceLongPressAction(longPressAction)) {
                asrkbVoiceHoldController.start()
            } else if (key.popup.isNotEmpty()) {
                dismissPopupPreview()
                showPopupKeyboard()
            } else if (hasLongPress) {
                longPressAction?.let {
                    processKeyAction(it, KeyBehavior.LONG_CLICK)
                    setPressedState(false)
                    dismissPopupPreview()
                }
            }
        }

        onMove = { x, y, isLongPress ->
            if (isLongPress && hasPopup) {
                popup.listener.onPopupAction(PopupAction.ChangeFocusAction(id, x, y))
            }
        }

        onCancel = {
            deletedTextBuffer.clear()
            asrkbVoiceHoldController.stopIfStarted()
            setPressedState(false)
            dismissPopupPreview()
        }
    }

    fun setPressedState(pressed: Boolean) {
        if (keyPressed != pressed) {
            keyPressed = pressed
            if (pressed) {
                key.onPressed()
            } else {
                key.onReleased()
            }
            invalidate()
        }
    }

    private fun isVoiceLongPressAction(action: KeyAction?): Boolean = action?.code == KeyEvent.KEYCODE_VOICE_ASSIST

    private fun processKeyAction(action: KeyAction, behavior: KeyBehavior) {
        Timber.d("processKeyAction: label=${key.getLabel()}, code=${action.code}, type=$behavior")

        if (action.isModifierKey) {
            keyboard.clickModifierKey(
                action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK),
                action.modifierKeyOnMask,
            )
            keyboardView.invalidateAllKeys()
            return
        }

        keyboardActionListener.onAction(action)

        val hookArrow = if (hookShiftArrow) {
            when (action.code) {
                in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> true
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> true
                else -> false
            }
        } else {
            false
        }

        if (!hookArrow) {
            if (keyboard.refreshModifier()) {
                keyboardView.invalidateAllKeys()
            }
        }
    }

    private fun showPopupKeyboard() {
        val popupKeys = key.popup
        if (popupKeys.isEmpty()) return

        popup.listener.onPopupAction(
            PopupAction.ShowKeyboardAction(id, popupKeys, bounds),
        )
    }

    private fun showPopupPreview(behavior: KeyBehavior = KeyBehavior.CLICK) {
        if (!keyboardView.popupOnKeyPress) return
        key.getPreviewText(behavior).takeIf { it.isNotEmpty() }?.let { previewText ->
            val context = if (previewText.isIconFont) {
                previewText
            } else {
                String(Character.toChars(previewText.codePointAt(0)))
            }
            popup.listener.onPopupAction(PopupAction.PreviewAction(id, context, bounds))
        }
    }

    private fun dismissPopupPreview() {
        popup.listener.onPopupAction(
            PopupAction.DismissAction(id),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        val desiredWidth = totalWidth + paddingLeft + paddingRight
        val desiredHeight = key.height + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        boundsValid = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas, key)

        val label = key.getLabel().let {
            if (it == "enter_labels") keyboardView.labelEnter else it
        }

        if (label.isNotEmpty()) {
            drawLabel(canvas, label)
        }

        val symbol = key.symbolLabel
        if (symbol.isNotEmpty()) {
            drawSymbol(canvas, symbol)
        }

        val hint = key.hint
        if (hint.isNotEmpty()) {
            drawSymbol(canvas, hint, isTop = false)
        }
    }

    private fun drawSymbol(canvas: Canvas, text: String, isTop: Boolean = true) {
        val showSymbol = rime.run { !getRuntimeOption("_hide_key_symbol") }
        val showHint = rime.run { !getRuntimeOption("_hide_key_hint") }

        if (isTop && !showSymbol) return
        if (!isTop && !showHint) return

        val textColor = key.getSymbolColor()
        val textSize = sp(key.symbolTextSize.takeIf { it > 0f } ?: keyboardView.symbolTextSize)
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY

        if (text.isIconFont) {
            drawIcon(canvas, text, textSize.toInt(), textColor, offsetX, offsetY, isTop)
        } else {
            symbolPaint.apply {
                color = textColor
                this.textSize = textSize
                typeface = FontManager.getTypeface("symbol_font")
            }

            val hasRichText = text.contains(Regex("<(/?b>|/?c(=|>)|/?s(=|>))"))

            if (hasRichText) {
                // 有富文本标签，使用富文本绘制
                val lines = parseRichText(text)
                val (centerX, linePositions) = calculateTextPosition(lines, offsetX, offsetY, isTop, isDynamic = true)

                drawRichText(canvas, lines, centerX, linePositions)
            } else {
                // 没有富文本标签，使用原版绘制逻辑
                val lines = text.split("\n")
                val (centerX, linePositions) = calculateTextPosition(lines, offsetX, offsetY, isTop)

                for (i in lines.indices) {
                    val (lineY, _) = linePositions[i]
                    canvas.drawText(lines[i], centerX, lineY, symbolPaint)
                }
            }
        }
    }

    /**
     * 计算文本绘制位置
     * @param lines 文本行列表
     * @param isDynamic 是否使用动态行高（默认false）
     */
    private fun calculateTextPosition(
        lines: List<*>,
        offsetX: Float,
        offsetY: Float,
        isTop: Boolean,
        isDynamic: Boolean = false,
    ): Pair<Float, List<Pair<Float, Float>>> {
        val fontMetrics = symbolPaint.fontMetrics
        val baseLineHeight = fontMetrics.descent - fontMetrics.ascent

        val totalHeight: Float
        val linePositions = mutableListOf<Pair<Float, Float>>()

        var currentY = if (isTop) {
            paddingTop - fontMetrics.top + sp(offsetY)
        } else {
            height - paddingBottom - fontMetrics.bottom + sp(offsetY)
        }

        if (isDynamic) {
            @Suppress("UNCHECKED_CAST")
            val richTextLines = lines as List<RichTextLine>
            val scaleHeights = richTextLines.map { line ->
                baseLineHeight * line.maxScale
            }
            totalHeight = scaleHeights.sum()
            currentY -= (totalHeight - baseLineHeight) / 2

            richTextLines.forEach { line ->
                val scale = line.maxScale
                val height = baseLineHeight * scale
                val lineY = currentY + (height - baseLineHeight) / 2
                linePositions.add(Pair(lineY, height))
                currentY += height
            }
        } else {
            totalHeight = baseLineHeight * lines.size
            currentY -= (totalHeight - baseLineHeight) / 2

            repeat(lines.size) {
                linePositions.add(Pair(currentY, baseLineHeight))
                currentY += baseLineHeight
            }
        }

        val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)

        return Pair(centerX, linePositions)
    }

    /**
     * 绘制富文本到 canvas
     */
    private fun drawRichText(canvas: Canvas, lines: List<RichTextLine>, x: Float, linePositions: List<Pair<Float, Float>>) {
        symbolPaint.textAlign = Paint.Align.CENTER

        lines.forEachIndexed { index, line ->
            val (lineY, _) = linePositions[index]
            drawRichTextLine(canvas, line, x, lineY, symbolPaint)
        }
    }

    /**
     * 绘制带样式的文本行
     */
    private fun drawRichTextLine(canvas: Canvas, line: RichTextLine, x: Float, y: Float, basePaint: Paint) {
        if (line.segments.isEmpty()) {
            // 没有样式，直接绘制
            canvas.drawText(line.text, x, y, basePaint)
            return
        }

        val paint = basePaint.copy()

        val baseTextSize = basePaint.textSize
        val fontMetrics = basePaint.fontMetrics
        val baseAscent = fontMetrics.ascent
        val baseDescent = fontMetrics.descent
        val symbolTypeface = FontManager.getTypeface("symbol_font")
        val boldTypeface = Typeface.create(symbolTypeface, Typeface.BOLD)

        val totalWidth = line.segments.sumOf { segment ->
            val textSize = segment.scale?.let { baseTextSize * it } ?: baseTextSize
            paint.textSize = textSize
            paint.measureText(segment.text).toDouble()
        }

        // 重要：将 textAlign 改为 LEFT，因为我们使用绝对位置绘制
        paint.textAlign = Paint.Align.LEFT
        var currentX = x - totalWidth.toFloat() / 2

        line.segments.forEach { segment ->
            paint.color = basePaint.color
            paint.textSize = baseTextSize
            paint.typeface = basePaint.typeface

            segment.color?.let { paint.color = it }

            var adjustedY = y
            segment.scale?.let { scale ->
                paint.textSize = baseTextSize * scale
                val scaledAscent = baseAscent * scale
                val scaledDescent = baseDescent * scale
                adjustedY = y + (baseAscent + baseDescent - scaledAscent - scaledDescent) / 2
            }
            if (segment.bold) {
                paint.typeface = boldTypeface
            }

            canvas.drawText(segment.text, currentX, adjustedY, paint)
            currentX += paint.measureText(segment.text)
        }
    }

    /**
     * 复制 Paint 对象
     */
    private fun Paint.copy(): Paint {
        val newPaint = Paint(this)
        newPaint.typeface = this.typeface
        return newPaint
    }

    private fun drawBackground(canvas: Canvas, k: Key) {
        val bg = k.getBackgroundDrawable() ?: return

        if (bg is GradientDrawable) {
            (k.roundCorner ?: keyboard.roundCorner).takeIf { it > 0f }?.let { bg.cornerRadius = dp(it) }
            (k.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let { bg.setStroke(dp(it), ColorManager.getColor("key_border_color")) }
        }

        bg.setBounds(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom,
        )
        bg.draw(canvas)
    }

    private fun drawLabel(canvas: Canvas, label: String) {
        val textColor = key.getTextColor()
        val textSize = sp(key.keyTextSize.takeIf { it > 0 } ?: if (label.length > 1) keyboardView.keyLongTextSize else keyboardView.keyTextSize)

        if (label.isIconFont) {
            drawIcon(canvas, label, textSize.toInt(), textColor, key.keyTextOffsetX, key.keyTextOffsetY)
        } else {
            textPaint.apply {
                color = textColor
                this.textSize = textSize
                typeface = FontManager.getTypeface("key_font")
                clearShadowLayer()
            }

            val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft
            val centerY = (height - paddingTop - paddingBottom) / 2f + paddingTop
            val fontMetrics = textPaint.fontMetrics
            val adjustmentY = -(fontMetrics.ascent + fontMetrics.descent) / 2f

            canvas.drawText(label, centerX + sp(key.keyTextOffsetX), centerY + adjustmentY + sp(key.keyTextOffsetY), textPaint)
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        iconName: String,
        size: Int,
        color: Int,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        isTop: Boolean? = null,
    ) {
        val halfSize = size / 2

        val cmdName = iconName.toIconName()
        val icon = if (cachedIconName == cmdName) {
            cachedIcon!!
        } else {
            IconicsDrawable(context, cmdName).apply {
                sizeDp = size
            }.also {
                cachedIcon = it
                cachedIconName = cmdName
            }
        }

        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)

        val centerY = when (isTop) {
            true -> paddingTop + halfSize + sp(offsetY)
            false -> height - paddingBottom - size + sp(offsetY)
            null -> (height - paddingTop - paddingBottom) / 2f + paddingTop + sp(offsetY)
        }

        icon.setBounds(
            (centerX - halfSize).toInt(),
            (centerY - halfSize).toInt(),
            (centerX + halfSize).toInt(),
            (centerY + halfSize).toInt(),
        )
        icon.draw(canvas)
    }

    /**
     * 解析富文本标签，返回每行的样式分段信息
     */
    private fun parseRichText(text: String): List<RichTextLine> {
        // 先解析出所有分段
        val segments = mutableListOf<StyledSegment>()
        val segmentBuilder = StringBuilder()

        var i = 0
        var currentColor: Int? = null
        var currentScale: Float? = null
        var currentBold = false

        fun flushSegment() {
            if (segmentBuilder.isNotEmpty()) {
                segments.add(
                    StyledSegment(
                        text = segmentBuilder.toString(),
                        color = currentColor,
                        scale = currentScale,
                        bold = currentBold,
                    ),
                )
                segmentBuilder.clear()
            }
        }

        while (i < text.length) {
            when {
                // 处理转义字符（只处理 < > \）
                text[i] == '\\' && i + 1 < text.length -> {
                    val nextChar = text[i + 1]
                    when (nextChar) {
                        '<', '>', '\\' -> {
                            segmentBuilder.append(nextChar)
                            i += 2
                        }
                        else -> {
                            segmentBuilder.append(text[i])
                            i++
                        }
                    }
                }
                // 处理标签
                text[i] == '<' -> {
                    val endTagIndex = text.indexOf('>', i)
                    if (endTagIndex == -1) {
                        segmentBuilder.append(text[i])
                        i++
                        continue
                    }

                    val tagContent = text.substring(i + 1, endTagIndex)

                    // 在标签前保存之前的文本段
                    flushSegment()

                    // 解析标签并更新状态
                    when {
                        tagContent == "b" -> currentBold = true
                        tagContent == "/b" -> currentBold = false
                        tagContent.startsWith("c=") -> {
                            val colorStr = tagContent.substring(2)
                            currentColor = try {
                                ColorManager.getColor(colorStr)
                            } catch (e: Exception) {
                                try {
                                    android.graphics.Color.parseColor(
                                        if (colorStr.startsWith("#")) colorStr else "#$colorStr",
                                    )
                                } catch (e2: Exception) {
                                    currentColor
                                }
                            }
                        }
                        tagContent == "/c" -> currentColor = null
                        tagContent.startsWith("s=") -> {
                            val scaleStr = tagContent.substring(2)
                            try {
                                currentScale = scaleStr.toFloat()
                            } catch (e: Exception) {
                                // 保持当前缩放
                            }
                        }
                        tagContent == "/s" -> currentScale = null
                        else -> {
                            // 未知标签，作为普通文本
                            segmentBuilder.append("<$tagContent>")
                        }
                    }

                    i = endTagIndex + 1
                }
                // 普通字符
                else -> {
                    segmentBuilder.append(text[i])
                    i++
                }
            }
        }

        // 保存最后一段
        flushSegment()
        // 按换行符分割成多行
        return buildLines(segments)
    }

    /**
     * 将分段按换行符分割成多行
     */
    private fun buildLines(segments: List<StyledSegment>): List<RichTextLine> {
        val lines = mutableListOf<RichTextLine>()
        val currentLineSegments = mutableListOf<StyledSegment>()

        fun addCurrentLine() {
            val maxScale = currentLineSegments.maxOfOrNull { it.scale ?: 1.0f } ?: 1.0f
            lines.add(RichTextLine(currentLineSegments.joinToString("") { it.text }, currentLineSegments.toList(), maxScale))
            currentLineSegments.clear()
        }

        segments.forEach { segment ->
            val parts = segment.text.split("\n")
            if (parts.size == 1) {
                // 没有换行符，直接添加到当前行
                currentLineSegments.add(segment)
            } else {
                // 有换行符，分割成多段
                parts.forEachIndexed { index: Int, part: String ->
                    if (part.isNotEmpty()) {
                        currentLineSegments.add(
                            StyledSegment(
                                text = part,
                                color = segment.color,
                                scale = segment.scale,
                                bold = segment.bold,
                            ),
                        )
                    }
                    // 除了最后一段，前面每段后面都有换行符，需要开始新行
                    if (index < parts.size - 1) {
                        addCurrentLine()
                    }
                }
            }
        }

        // 添加最后一行
        addCurrentLine()

        return lines.ifEmpty { listOf(RichTextLine("", emptyList())) }
    }
}

/**
 * 样式分段：存储一段文本及其样式
 */
private data class StyledSegment(
    val text: String,
    val color: Int?, // null 表示使用默认颜色
    val scale: Float?, // null 表示使用默认大小（缩放比例）
    val bold: Boolean, // 是否加粗
)

/**
 * 富文本行：存储一行的文本和样式分段
 */
private data class RichTextLine(
    val text: String, // 整行文本
    val segments: List<StyledSegment>, // 样式分段
    val maxScale: Float = 1.0f, // 该行最大字体缩放比例（用于计算行高）
)
