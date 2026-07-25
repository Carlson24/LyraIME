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
import com.osfans.trime.data.theme.model.TextKeyboard
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

        val actionLabel = key.getLabel()

        val labelSegments = if (actionLabel == "enter_labels") {
            listOf(TextKeyboard.LabelSegment(text = keyboardView.labelEnter))
        } else if (key.label.isNotEmpty()) {
            if (key.label.any { it.text.isNotEmpty() }) {
                key.label
            } else if (actionLabel.isNotEmpty()) {
                key.label.map { it.copy(text = it.text.ifEmpty { actionLabel }) }
            } else {
                emptyList()
            }
        } else if (actionLabel.isNotEmpty()) {
            listOf(TextKeyboard.LabelSegment(text = actionLabel))
        } else {
            emptyList()
        }

        if (labelSegments.isNotEmpty()) {
            drawLabelSegments(canvas, labelSegments)
        }

        if (key.symbolLabel.isNotEmpty()) {
            drawSymbolSegments(canvas, key.symbolLabel, isTop = true)
        }

        if (key.hint.isNotEmpty()) {
            drawSymbolSegments(canvas, key.hint, isTop = false)
        }
    }

    private fun drawSymbolSegments(canvas: Canvas, segments: List<TextKeyboard.LabelSegment>, isTop: Boolean) {
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
        val iconSize = textSize.toInt()
        val fontKey = if (isTop) "symbol_font" else "hint_font"
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY
        val mode = if (isTop) PositionMode.TOP else PositionMode.BOTTOM

        drawSegmentsImpl(canvas, segments, textSize, iconSize, textColor, offsetX, offsetY, mode, fontKey, symbolPaint)
    }

    private fun drawLabelSegments(canvas: Canvas, segments: List<TextKeyboard.LabelSegment>) {
        val textColor = key.getTextColor()
        val plainText = segments.joinToString("") { it.text }
        val textSize = sp(
            key.keyTextSize.takeIf { it > 0 }
                ?: if (plainText.length > 1) keyboardView.keyLongTextSize else keyboardView.keyTextSize,
        )
        val iconSize = textSize.toInt()

        drawSegmentsImpl(
            canvas, segments, textSize, iconSize, textColor,
            key.keyTextOffsetX, key.keyTextOffsetY, PositionMode.CENTER, "key_font", textPaint,
        )
    }

    private fun drawSegmentsImpl(
        canvas: Canvas,
        segments: List<TextKeyboard.LabelSegment>,
        textSize: Float,
        iconSize: Int,
        textColor: Int,
        offsetX: Float,
        offsetY: Float,
        mode: PositionMode,
        fontKey: String,
        paint: Paint,
    ) {
        if (segments.isEmpty()) return

        val richTextLines = segmentsToRichTextLines(segments)

        paint.apply {
            color = textColor
            this.textSize = textSize
            typeface = FontManager.getTypeface(fontKey)
            fontFeatureSettings = FontManager.fontFeatureSettings
            clearShadowLayer()
        }

        val (centerX, linePositions) = calculateTextPosition(
            richTextLines,
            offsetX,
            offsetY,
            mode,
            paint.fontMetrics,
            isDynamic = true,
        )

        drawRichText(canvas, richTextLines, centerX, linePositions, paint, offsetX, textSize, iconSize, textColor)
    }

    private fun segmentsToRichTextLines(segments: List<TextKeyboard.LabelSegment>): List<RichTextLine> {
        val lines = mutableListOf<RichTextLine>()
        var currentSegs = mutableListOf<StyledSegment>()

        fun buildLine(): RichTextLine {
            val maxScale = currentSegs.maxOfOrNull { it.scale ?: 1.0f } ?: 1.0f
            val text = currentSegs.joinToString("") { it.text }
            return RichTextLine(text, currentSegs.toList(), maxScale)
        }

        for (seg in segments) {
            val parts = seg.text.split("\n")
            for (i in parts.indices) {
                if (i > 0) {
                    lines.add(buildLine())
                    currentSegs = mutableListOf()
                }
                if (parts[i].isEmpty()) continue
                if (seg.align == TextKeyboard.Align.JUSTIFY && parts[i].length > 1) {
                    for (ch in parts[i]) {
                        currentSegs.add(
                            StyledSegment(
                                text = ch.toString(),
                                colorKey = seg.color,
                                scale = seg.scale,
                                bold = seg.bold,
                                horizontalAlign = TextKeyboard.Align.JUSTIFY,
                            ),
                        )
                    }
                } else {
                    currentSegs.add(
                        StyledSegment(
                            text = parts[i],
                            colorKey = seg.color,
                            scale = seg.scale,
                            bold = seg.bold,
                            horizontalAlign = seg.align ?: TextKeyboard.Align.CENTER,
                        ),
                    )
                }
            }
        }
        lines.add(buildLine())
        return lines.ifEmpty { listOf(RichTextLine("", emptyList())) }
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
            val uniform = k.roundCorner ?: keyboard.roundCorner
            fun r(value: Float?): Float = (value ?: uniform).takeIf { it > 0f } ?: 0f
            val tl = r(k.roundedCornerTopLeft)
            val tr = r(k.roundedCornerTopRight)
            val bl = r(k.roundedCornerBottomLeft)
            val br = r(k.roundedCornerBottomRight)
            if (tl == tr && tl == bl && tl == br && tl == uniform) {
                uniform.takeIf { it > 0f }?.let { bg.cornerRadius = dp(it) }
            } else {
                bg.cornerRadii = floatArrayOf(
                    dp(tl),
                    dp(tl),
                    dp(tr),
                    dp(tr),
                    dp(br),
                    dp(br),
                    dp(bl),
                    dp(bl),
                )
            }
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

    private fun calculateTextPosition(
        lines: List<RichTextLine>,
        offsetX: Float,
        offsetY: Float,
        mode: PositionMode,
        fontMetrics: Paint.FontMetrics,
        isDynamic: Boolean = false,
    ): Pair<Float, List<Pair<Float, Float>>> {
        val baseLineHeight = fontMetrics.descent - fontMetrics.ascent

        var totalHeight: Float
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

        val scaleHeights = lines.map { line ->
            baseLineHeight * line.maxScale
        }
        totalHeight = scaleHeights.sum()
        currentY -= (totalHeight - baseLineHeight) / 2

        lines.forEach { line ->
            val scale = line.maxScale
            val height = baseLineHeight * scale
            val lineY = currentY + (height - baseLineHeight) / 2
            linePositions.add(Pair(lineY, height))
            currentY += height
        }

        val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)

        return Pair(centerX, linePositions)
    }

    private fun drawRichText(
        canvas: Canvas,
        lines: List<RichTextLine>,
        x: Float,
        linePositions: List<Pair<Float, Float>>,
        paint: Paint,
        offsetX: Float,
        baseTextSize: Float,
        iconSize: Int,
        textColor: Int,
    ) {
        paint.textAlign = Paint.Align.CENTER

        lines.forEachIndexed { index, line ->
            val (lineY, _) = linePositions[index]
            drawRichTextLine(canvas, line, x, lineY, paint, offsetX, baseTextSize, iconSize, textColor)
        }
    }

    private fun drawRichTextLine(
        canvas: Canvas,
        line: RichTextLine,
        x: Float,
        y: Float,
        basePaint: Paint,
        offsetX: Float,
        baseTextSize: Float,
        iconSize: Int,
        textColor: Int,
    ) {
        if (line.segments.isEmpty()) {
            basePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(UnicodeVariantUtils.toDisplay(line.text), x, y, basePaint)
            return
        }

        val fontMetrics = basePaint.fontMetrics
        val baseAscent = fontMetrics.ascent
        val baseDescent = fontMetrics.descent
        val baseTypeface = basePaint.typeface ?: Typeface.DEFAULT
        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)

        val leftGroup = mutableListOf<StyledSegment>()
        val centerGroup = mutableListOf<StyledSegment>()
        val rightGroup = mutableListOf<StyledSegment>()
        val justifyGroup = mutableListOf<StyledSegment>()
        for (seg in line.segments) {
            when (seg.horizontalAlign) {
                TextKeyboard.Align.LEFT -> leftGroup.add(seg)
                TextKeyboard.Align.RIGHT -> rightGroup.add(seg)
                TextKeyboard.Align.JUSTIFY -> justifyGroup.add(seg)
                else -> centerGroup.add(seg)
            }
        }

        fun isIcon(seg: StyledSegment): Boolean = seg.text.startsWith("ic@")

        fun measureSegments(group: List<StyledSegment>): Float = group.sumOf { seg ->
            if (isIcon(seg)) {
                iconSize.toDouble()
            } else {
                val sz = seg.scale?.let { baseTextSize * it } ?: baseTextSize
                richTextPaint.textSize = sz
                richTextPaint.typeface = if (seg.bold) boldTypeface else baseTypeface
                richTextPaint.measureText(seg.text).toDouble()
            }
        }.toFloat()

        val leftWidth = measureSegments(leftGroup)
        val centerWidth = measureSegments(centerGroup)
        val rightWidth = measureSegments(rightGroup)
        val justifyWidth = measureSegments(justifyGroup)

        val roundCornerPx = (key.roundCorner ?: keyboard.roundCorner)
            .takeIf { it > 0f }?.let { dp(it) } ?: 0f

        val paddedLeft = paddingLeft.toFloat() + sp(offsetX) + roundCornerPx * 2 / 3f
        val paddedRight = (width - paddingRight).toFloat() + sp(offsetX) - roundCornerPx * 2 / 3f

        val leftStart = paddedLeft
        val rightStart = paddedRight - rightWidth
        val centerStart = paddedLeft + (paddedRight - paddedLeft - centerWidth) / 2f

        fun drawGroup(group: List<StyledSegment>, startX: Float, gap: Float = 0f) {
            var currentX = startX
            group.forEach { seg ->
                if (isIcon(seg)) {
                    val cmdName = seg.text.replace("ic@", "cmd_")
                    val halfIcon = iconSize / 2f
                    val centerY = y + (baseAscent + baseDescent) / 2f - (iconSize / 4f)
                    drawIconAt(canvas, cmdName, iconSize, textColor, currentX + halfIcon, centerY + iconVerticalOffset)
                    currentX += iconSize + gap
                } else {
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
                    currentX += richTextPaint.measureText(seg.text) + gap
                }
            }
        }

        val spareStart = paddedLeft + leftWidth
        val spareEnd = paddedRight - rightWidth
        if (justifyGroup.isNotEmpty()) {
            val available = (spareEnd - spareStart - justifyWidth).coerceAtLeast(0f)
            val gap = if (justifyGroup.size > 1) available / (justifyGroup.size - 1) else 0f
            drawGroup(justifyGroup, spareStart, gap)
        }

        drawGroup(leftGroup, leftStart)
        drawGroup(centerGroup, centerStart)
        drawGroup(rightGroup, rightStart)
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
}

/**
 * 样式分段：存储一段文本及其样式。
 * colorKey 存储原始颜色标识符（主题键名或十六进制值），在绘制时解析以支持深色/浅色模式切换。
 */
private data class StyledSegment(
    val text: String,
    val colorKey: String?,
    val scale: Float?,
    val bold: Boolean,
    val horizontalAlign: com.osfans.trime.data.theme.model.TextKeyboard.Align = com.osfans.trime.data.theme.model.TextKeyboard.Align.CENTER,
)

private data class RichTextLine(
    val text: String,
    val segments: List<StyledSegment>,
    val maxScale: Float = 1.0f,
)

private enum class PositionMode { TOP, CENTER, BOTTOM }
