/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.KeyEvent
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizePx
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.link.AsrkbVoiceHoldSessionController
import com.osfans.trime.util.UnicodeVariantUtils
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
    private val richTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    private val iconCache = object : LruCache<String, IconicsDrawable>(4) {}
    private val iconVerticalOffset = dp(2).toFloat()

    private val richTextCache = mutableMapOf<String, List<RichTextLine>>()
    private var cachedRichTextSymbol: String? = null
    private var cachedRichTextSymbolResult: List<RichTextLine>? = null
    private var cachedRichTextLabel: String? = null
    private var cachedRichTextLabelResult: List<RichTextLine>? = null

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
                    delta > 0 -> keyboardActionListener.onAction(KeyAction("Right"))
                    delta < 0 -> keyboardActionListener.onAction(KeyAction("Left"))
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
            setLayerType(LAYER_TYPE_HARDWARE, null)
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

        keyboardActionListener.onAction(action, key, behavior)

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
        if (isTop && !keyboardView.showKeySymbols) return
        if (!isTop && !keyboardView.showKeyHints) return

        val textColor = key.getSymbolColor()
        val textSize = sp(
            if (isTop) {
                key.symbolTextSize.takeIf { it > 0f } ?: keyboardView.symbolTextSize
            } else {
                key.hintTextSize.takeIf { it > 0f } ?: keyboardView.hintTextSize
            },
        )
        val fontKey = if (isTop) "symbol_font" else "hint_font"
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY

        if (text.isIconFont) {
            val mode = if (isTop) PositionMode.TOP else PositionMode.BOTTOM
            drawSegments(canvas, text.parseLabelSegments(), textSize, textSize.toInt(), textColor, offsetX, offsetY, mode, fontKey, symbolPaint)
        } else {
            symbolPaint.apply {
                color = textColor
                this.textSize = textSize
                typeface = FontManager.getTypeface(fontKey)
                fontFeatureSettings = FontManager.fontFeatureSettings
            }

            val hasRichText = text.contains(Regex("<(/?b>|/?c(=|>)|/?s(=|>)|/?l>|/?r>)"))

            if (hasRichText) {
                val lines = if (text == cachedRichTextSymbol) {
                    cachedRichTextSymbolResult!!
                } else {
                    parseRichText(text).also {
                        cachedRichTextSymbol = text
                        cachedRichTextSymbolResult = it
                    }
                }
                val mode = if (isTop) PositionMode.TOP else PositionMode.BOTTOM
                val (centerX, linePositions) = calculateTextPosition(lines, offsetX, offsetY, mode, symbolPaint.fontMetrics, isDynamic = true)

                drawRichText(canvas, lines, centerX, linePositions, offsetX = offsetX)
            } else {
                // 没有富文本标签，使用原版绘制逻辑
                val lines = text.split("\n")
                val mode = if (isTop) PositionMode.TOP else PositionMode.BOTTOM
                val (centerX, linePositions) = calculateTextPosition(lines, offsetX, offsetY, mode, symbolPaint.fontMetrics)

                for (i in lines.indices) {
                    val (lineY, _) = linePositions[i]
                    canvas.drawText(UnicodeVariantUtils.toDisplay(lines[i]), centerX, lineY, symbolPaint)
                }
            }
        }
    }

    /**
     * 计算文本绘制位置
     * @param lines 文本行列表
     * @param fontMetrics 使用的字体度量
     * @param isDynamic 是否使用动态行高（默认false）
     */
    private fun calculateTextPosition(
        lines: List<*>,
        offsetX: Float,
        offsetY: Float,
        mode: PositionMode,
        fontMetrics: Paint.FontMetrics,
        isDynamic: Boolean = false,
    ): Pair<Float, List<Pair<Float, Float>>> {
        val baseLineHeight = fontMetrics.descent - fontMetrics.ascent

        val totalHeight: Float
        val linePositions = mutableListOf<Pair<Float, Float>>()

        var currentY = when (mode) {
            PositionMode.TOP -> paddingTop - fontMetrics.top + sp(offsetY)
            PositionMode.BOTTOM -> height - paddingBottom - fontMetrics.bottom + sp(offsetY)
            PositionMode.CENTER -> {
                val centerY = (height - paddingTop - paddingBottom) / 2f + paddingTop
                val adjustmentY = -(fontMetrics.ascent + fontMetrics.descent) / 2f
                centerY + adjustmentY + sp(offsetY)
            }
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
    private fun drawRichText(canvas: Canvas, lines: List<RichTextLine>, x: Float, linePositions: List<Pair<Float, Float>>, paint: Paint = symbolPaint, offsetX: Float = 0f) {
        paint.textAlign = Paint.Align.CENTER

        lines.forEachIndexed { index, line ->
            val (lineY, _) = linePositions[index]
            drawRichTextLine(canvas, line, x, lineY, paint, offsetX)
        }
    }

    /**
     * 绘制带样式的文本行
     */
    private fun drawRichTextLine(canvas: Canvas, line: RichTextLine, x: Float, y: Float, basePaint: Paint, offsetX: Float = 0f) {
        if (line.segments.isEmpty()) {
            canvas.drawText(UnicodeVariantUtils.toDisplay(line.text), x, y, basePaint)
            return
        }

        val baseTextSize = basePaint.textSize
        val fontMetrics = basePaint.fontMetrics
        val baseAscent = fontMetrics.ascent
        val baseDescent = fontMetrics.descent
        val baseTypeface = basePaint.typeface ?: Typeface.DEFAULT
        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)

        // 按水平对齐分组
        val leftGroup = mutableListOf<StyledSegment>()
        val centerGroup = mutableListOf<StyledSegment>()
        val rightGroup = mutableListOf<StyledSegment>()
        for (seg in line.segments) {
            when (seg.horizontalAlign) {
                HorizontalAlign.LEFT -> leftGroup.add(seg)
                HorizontalAlign.CENTER -> centerGroup.add(seg)
                HorizontalAlign.RIGHT -> rightGroup.add(seg)
            }
        }

        fun measureSegments(group: List<StyledSegment>): Float = group.sumOf { seg ->
            val textSize = seg.scale?.let { baseTextSize * it } ?: baseTextSize
            richTextPaint.textSize = textSize
            richTextPaint.typeface = if (seg.bold) boldTypeface else baseTypeface
            richTextPaint.measureText(seg.text).toDouble()
        }.toFloat()

        val leftWidth = measureSegments(leftGroup)
        val centerWidth = measureSegments(centerGroup)
        val rightWidth = measureSegments(rightGroup)

        val paddedLeft = paddingLeft.toFloat() + sp(offsetX)
        val paddedRight = (width - paddingRight).toFloat() + sp(offsetX)

        val leftStart = paddedLeft
        val rightStart = paddedRight - rightWidth
        val centerStart = if (rightGroup.isNotEmpty()) {
            (leftStart + leftWidth + rightStart - centerWidth) / 2f
        } else {
            paddedLeft + leftWidth + (paddedRight - paddedLeft - leftWidth - centerWidth) / 2f
        }

        fun drawGroup(group: List<StyledSegment>, startX: Float) {
            var currentX = startX
            group.forEach { seg ->
                richTextPaint.color = basePaint.color
                richTextPaint.textSize = baseTextSize
                richTextPaint.typeface = basePaint.typeface
                richTextPaint.textAlign = Paint.Align.LEFT

                seg.colorKey?.let { richTextPaint.color = resolveColor(it, basePaint.color) }

                var adjustedY = y
                seg.scale?.let { scale ->
                    richTextPaint.textSize = baseTextSize * scale
                    val scaledAscent = baseAscent * scale
                    val scaledDescent = baseDescent * scale
                    adjustedY = y + (baseAscent + baseDescent - scaledAscent - scaledDescent) / 2
                }
                if (seg.bold) {
                    richTextPaint.typeface = boldTypeface
                }

                canvas.drawText(UnicodeVariantUtils.toDisplay(seg.text), currentX, adjustedY, richTextPaint)
                currentX += richTextPaint.measureText(seg.text)
            }
        }

        drawGroup(leftGroup, leftStart)
        drawGroup(centerGroup, centerStart)
        drawGroup(rightGroup, rightStart)
    }

    /**
     * 在绘制时解析颜色标识符，支持深色/浅色模式动态切换。
     * 优先作为主题色键通过 ColorManager 查找，回退为直接解析颜色值。
     */
    private fun resolveColor(
        spec: String,
        fallback: Int,
    ): Int = try {
        ColorManager.getColor(spec)
    } catch (_: Exception) {
        try {
            Color.parseColor(if (spec.startsWith("#")) spec else "#$spec")
        } catch (_: Exception) {
            fallback
        }
    }

    private fun drawBackground(canvas: Canvas, k: Key) {
        val bg = k.getBackgroundDrawable() ?: return

        if (bg is GradientDrawable) {
            (k.roundCorner ?: keyboard.roundCorner).takeIf { it > 0f }?.let { bg.cornerRadius = dp(it) }
            (k.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let { bg.setStroke(dp(it), k.keyBorderColorValue) }
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
            drawSegments(canvas, label.parseLabelSegments(), textSize, textSize.toInt(), textColor, key.keyTextOffsetX, key.keyTextOffsetY, PositionMode.CENTER, "key_font")
            return
        }

        textPaint.apply {
            color = textColor
            this.textSize = textSize
            typeface = FontManager.getTypeface("key_font")
            fontFeatureSettings = FontManager.fontFeatureSettings
            clearShadowLayer()
        }

        val hasNewline = '\n' in label
        val hasRichText = label.contains(Regex("<(/?b>|/?c(=|>)|/?s(=|>)|/?l>|/?r>)"))

        val offsetX = key.keyTextOffsetX
        val offsetY = key.keyTextOffsetY

        if (hasRichText) {
            val lines = if (label == cachedRichTextLabel) {
                cachedRichTextLabelResult!!
            } else {
                parseRichText(label).also {
                    cachedRichTextLabel = label
                    cachedRichTextLabelResult = it
                }
            }
            val (centerX, linePositions) = calculateTextPosition(
                lines,
                offsetX,
                offsetY,
                PositionMode.CENTER,
                textPaint.fontMetrics,
                isDynamic = true,
            )
            drawRichText(canvas, lines, centerX, linePositions, textPaint, offsetX)
        } else if (hasNewline) {
            val lines = label.split("\n")
            val (centerX, linePositions) = calculateTextPosition(
                lines,
                offsetX,
                offsetY,
                PositionMode.CENTER,
                textPaint.fontMetrics,
            )
            for (i in lines.indices) {
                val (lineY, _) = linePositions[i]
                canvas.drawText(UnicodeVariantUtils.toDisplay(lines[i]), centerX, lineY, textPaint)
            }
        } else {
            val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft
            val centerY = (height - paddingTop - paddingBottom) / 2f + paddingTop
            val fontMetrics = textPaint.fontMetrics
            val adjustmentY = -(fontMetrics.ascent + fontMetrics.descent) / 2f

            canvas.drawText(UnicodeVariantUtils.toDisplay(label), centerX + sp(offsetX), centerY + adjustmentY + sp(offsetY), textPaint)
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
        val cmdName = iconName.toIconName()
        val halfSize = size / 2

        val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)

        val centerY = when (isTop) {
            true -> paddingTop + halfSize + sp(offsetY)
            false -> height - paddingBottom - size + sp(offsetY)
            null -> (height - paddingTop - paddingBottom) / 2f + paddingTop + sp(offsetY)
        }

        drawIconAt(canvas, cmdName, size, color, centerX, centerY)
    }

    private fun drawIconAt(
        canvas: Canvas,
        cmdName: String,
        size: Int,
        color: Int,
        centerX: Float,
        centerY: Float,
    ) {
        val halfSize = size / 2

        val icon = iconCache[cmdName] ?: IconicsDrawable(context, cmdName).apply {
            sizePx = size
        }.also { iconCache.put(cmdName, it) }

        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        icon.setBounds(
            (centerX - halfSize).toInt(),
            (centerY - halfSize).toInt(),
            (centerX + halfSize).toInt(),
            (centerY + halfSize).toInt(),
        )
        icon.draw(canvas)
    }

    private fun drawSegments(
        canvas: Canvas,
        segments: List<LabelSegment>,
        textSize: Float,
        iconSize: Int,
        textColor: Int,
        offsetX: Float,
        offsetY: Float,
        mode: PositionMode,
        fontKey: String? = null,
        paint: Paint = textPaint,
    ) {
        val savedAlign = paint.textAlign

        paint.apply {
            color = textColor
            this.textSize = textSize
            if (fontKey != null) typeface = FontManager.getTypeface(fontKey)
            fontFeatureSettings = FontManager.fontFeatureSettings
            clearShadowLayer()
            textAlign = Paint.Align.LEFT
        }

        val spacing = 0f
        val iconPx = iconSize.toFloat()
        val fontMetrics = paint.fontMetrics

        val widths = segments.map { seg ->
            when (seg) {
                is LabelSegment.Icon -> iconPx
                is LabelSegment.Text -> paint.measureText(UnicodeVariantUtils.toDisplay(seg.content))
            }
        }

        val visualCenterY = (height - paddingTop - paddingBottom) / 2f + paddingTop + sp(offsetY)
        val halfIcon = iconPx / 2f

        val iconCenterY: Float
        val textBaselineY: Float

        when (mode) {
            PositionMode.CENTER -> {
                iconCenterY = visualCenterY + iconVerticalOffset
                textBaselineY = visualCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            }
            PositionMode.TOP -> {
                iconCenterY = paddingTop + halfIcon + sp(offsetY) + iconVerticalOffset
                textBaselineY = paddingTop - fontMetrics.top + sp(offsetY)
            }
            PositionMode.BOTTOM -> {
                iconCenterY = height - paddingBottom - iconPx.toInt() + sp(offsetY) + iconVerticalOffset
                textBaselineY = height - paddingBottom - fontMetrics.bottom + sp(offsetY)
            }
        }

        // 按对齐方向分组
        data class IndexedSegment(val index: Int, val seg: LabelSegment, val width: Float)

        val leftGroup = mutableListOf<IndexedSegment>()
        val centerGroup = mutableListOf<IndexedSegment>()
        val rightGroup = mutableListOf<IndexedSegment>()

        for (i in segments.indices) {
            val item = IndexedSegment(i, segments[i], widths[i])
            when (segments[i].align) {
                HorizontalAlign.LEFT -> leftGroup.add(item)
                HorizontalAlign.CENTER -> centerGroup.add(item)
                HorizontalAlign.RIGHT -> rightGroup.add(item)
            }
        }

        fun measureGroup(group: List<IndexedSegment>): Float = group.sumOf { it.width.toDouble() }.toFloat() +
            spacing * (group.size - 1).coerceAtLeast(0)

        val leftTotal = measureGroup(leftGroup)
        val centerTotal = measureGroup(centerGroup)
        val rightTotal = measureGroup(rightGroup)

        val paddedLeft = paddingLeft.toFloat() + sp(offsetX)
        val paddedRight = (width - paddingRight).toFloat() + sp(offsetX)

        val leftStart = paddedLeft
        val rightStart = paddedRight - rightTotal
        val centerStart = if (rightGroup.isNotEmpty()) {
            (leftStart + leftTotal + rightStart - centerTotal) / 2f
        } else {
            paddedLeft + leftTotal + (paddedRight - paddedLeft - leftTotal - centerTotal) / 2f
        }

        fun drawGroup(group: List<IndexedSegment>, startX: Float) {
            var cursorX = startX
            for (item in group) {
                when (item.seg) {
                    is LabelSegment.Icon -> {
                        drawIconAt(canvas, item.seg.cmdName, iconSize, textColor, cursorX + item.width / 2f, iconCenterY)
                    }
                    is LabelSegment.Text -> {
                        canvas.drawText(UnicodeVariantUtils.toDisplay(item.seg.content), cursorX, textBaselineY, paint)
                    }
                }
                cursorX += item.width + spacing
            }
        }

        drawGroup(leftGroup, leftStart)
        drawGroup(centerGroup, centerStart)
        drawGroup(rightGroup, rightStart)

        paint.textAlign = savedAlign
    }

    /**
     * 解析富文本标签，返回每行的样式分段信息
     */
    private fun parseRichText(text: String): List<RichTextLine> {
        // 先解析出所有分段
        val segments = mutableListOf<StyledSegment>()
        val segmentBuilder = StringBuilder()

        var i = 0
        var currentColorKey: String? = null
        var currentScale: Float? = null
        var currentBold = false
        var currentHorizontalAlign = HorizontalAlign.CENTER

        fun flushSegment() {
            if (segmentBuilder.isNotEmpty()) {
                segments.add(
                    StyledSegment(
                        text = segmentBuilder.toString(),
                        colorKey = currentColorKey,
                        scale = currentScale,
                        bold = currentBold,
                        horizontalAlign = currentHorizontalAlign,
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
                        tagContent == "l" -> currentHorizontalAlign = HorizontalAlign.LEFT
                        tagContent == "/l" -> currentHorizontalAlign = HorizontalAlign.CENTER
                        tagContent == "r" -> currentHorizontalAlign = HorizontalAlign.RIGHT
                        tagContent == "/r" -> currentHorizontalAlign = HorizontalAlign.CENTER
                        tagContent.startsWith("c=") -> {
                            currentColorKey = tagContent.substring(2)
                        }
                        tagContent == "/c" -> currentColorKey = null
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
                                colorKey = segment.colorKey,
                                scale = segment.scale,
                                bold = segment.bold,
                                horizontalAlign = segment.horizontalAlign,
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
 * 样式分段：存储一段文本及其样式。
 * colorKey 存储原始颜色标识符（主题键名或十六进制值），在绘制时解析以支持深色/浅色模式切换。
 */
private data class StyledSegment(
    val text: String,
    val colorKey: String?, // null 表示使用默认颜色；非 null 为颜色标识符字符串
    val scale: Float?, // null 表示使用默认大小（缩放比例）
    val bold: Boolean, // 是否加粗
    val horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER, // 水平对齐方向
)

/**
 * 富文本行：存储一行的文本和样式分段
 */
private data class RichTextLine(
    val text: String, // 整行文本
    val segments: List<StyledSegment>, // 样式分段
    val maxScale: Float = 1.0f, // 该行最大字体缩放比例（用于计算行高）
)

private enum class PositionMode { TOP, CENTER, BOTTOM }
