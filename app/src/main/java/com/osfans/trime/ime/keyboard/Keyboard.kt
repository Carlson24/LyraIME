/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsets
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.isLandscape
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.systemservices.windowManager
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

internal object KeyboardPending {
    var lastIsPortrait: Boolean? = null
    var containerWidth: Int = 0
    var allowedWidth: Int = 0
}

/** 從YAML中加載鍵盤配置，包含多個[按鍵][Key]。  */
@Suppress("ktlint:standard:property-naming")
class Keyboard(
    private val context: Context,
    private val theme: Theme,
    selfConfig: TextKeyboard? = null,
) {

    /** 按鍵默認水平間距  */
    internal val horizontalGap: Int =
        intArrayOf(
            selfConfig?.horizontalGap ?: 0,
            theme.generalStyle.horizontalGap,
        ).firstOrNull { it > 0 }?.let { context.dp(it) } ?: 0

    /** 单手模式下水平间距缩放因子 */
    internal var horizontalGapScale: Float = 1.0f

    /** 默認行距  */
    internal val verticalGap: Int =
        intArrayOf(
            selfConfig?.verticalGap ?: 0,
            theme.generalStyle.verticalGap,
        ).firstOrNull { it > 0 }?.let { context.dp(it) } ?: 0

    /** 鍵盤第一行上方留白  */
    internal val keyboardPaddingTop: Int =
        intArrayOf(
            selfConfig?.keyboardPaddingTop ?: 0,
            theme.generalStyle.keyboardPaddingTop,
        ).firstOrNull { it > 0 }?.let { context.dp(it) } ?: 0

    /** 默認按鍵圓角半徑  */
    val roundCorner: Float =
        selfConfig?.roundCorner?.takeIf { it >= 0f } ?: theme.generalStyle.roundCorner

    /** 默認按鍵邊框寬度  */
    val keyBorder: Int =
        selfConfig?.keyBorder?.takeIf { it >= 0 } ?: theme.generalStyle.keyBorder

    val keyShadowRadius: Float =
        selfConfig?.keyShadowRadius?.takeIf { it >= 0f } ?: theme.generalStyle.keyShadowRadius
    val keyShadowDirection: List<String>? =
        selfConfig?.keyShadowDirection ?: theme.generalStyle.keyShadowDirection.takeIf { it.isNotEmpty() }

    /** 鍵盤的Shift鍵  */
    var mShiftKey: Key? = null
    var mCtrlKey: Key? = null
    var mAltKey: Key? = null
    var mMetaKey: Key? = null
    var mSymKey: Key? = null

    /**
     * Total height of the keyboard, including the padding and keys
     *
     * @return the total height of the keyboard
     */
    var height = 0
        private set

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the right
     * side.
     */
    var minWidth = 0
        private set

    /** List of keys in this keyboard  */
    private val mKeys = mutableListOf<Key>()
    val appearanceStateKeys = mutableListOf<Key>()
    var modifier = 0
        private set

    var firstPressedKeyIndex: Int = -1

    /** Width of the screen available to fit the keyboard  */
    private val allowedWidth: Int
        get() {
            val isPortrait = !context.resources.configuration.isLandscape()

            if (KeyboardPending.containerWidth > 0 && KeyboardPending.lastIsPortrait == isPortrait) {
                return KeyboardPending.containerWidth
            }

            val padding = theme.generalStyle.run {
                if (context.isLandscapeMode()) keyboardPaddingLand else keyboardPadding
            }

            val totalPadding = 2 * padding

            val safeWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = context.windowManager.maximumWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                val displayWidth = context.resources.displayMetrics.widthPixels
                val windowWidth = windowMetrics.bounds.width() - insets.left - insets.right
                if (windowWidth < displayWidth - context.dp(1)) displayWidth else windowWidth
            } else {
                @Suppress("DEPRECATION")
                val size = Point()
                @Suppress("DEPRECATION")
                context.windowManager.defaultDisplay.getSize(size)
                size.x
            }

            val width = safeWidth - context.dp(totalPadding)
            KeyboardPending.allowedWidth = width
            return width
        }

    /** Keyboard default ascii mode  */
    val asciiMode = selfConfig?.asciiMode ?: false
    val resetAsciiMode = selfConfig?.resetAsciiMode ?: true
    var lastAsciiMode: Boolean = asciiMode

    val landscapeKeyboard: String? = selfConfig?.landscapeKeyboard
    private val preferredSplitPercent by AppPrefs.defaultInstance().keyboard.splitSpacePercent
    private val landscapePercent =
        intArrayOf(
            selfConfig?.landscapeSplitPercent ?: 0,
            preferredSplitPercent,
        ).firstOrNull { it > 0 } ?: 0

    // Variables for pre-computing nearest keys.
    private var labelTransform = selfConfig?.labelTransform ?: TextKeyboard.LabelTransform.NONE
    private var mCellWidth = 0
    private var mCellHeight = 0
    private var gridNeighbors: Array<IntArray?>? = null

    private val proximityThreshold: Int
        get() {
            val defaultWidth = (allowedWidth * 0.1f).toInt()
            return (defaultWidth * SEARCH_DISTANCE).pow(2).toInt()
        }
    val isLock = selfConfig?.lock ?: false // 切換程序時記憶鍵盤
    val isT9Mode: Boolean = selfConfig?.t9Mode ?: false
    val isDynamicMode: Boolean = selfConfig?.dynamicMode ?: false
    val dynamicOriginal: String = selfConfig?.dynamicOriginal?.takeIf { it.isNotEmpty() } ?: ".default"
    val t9SidebarWidth: Float = selfConfig?.t9SidebarWidth ?: 0.15f
    val t9SidebarPosition: String = selfConfig?.t9SidebarPosition ?: "left"
    val t9SidebarSpanRows: Int = selfConfig?.t9SidebarSpanRows ?: 3
    val t9SidebarShowItems: Int = selfConfig?.t9SidebarShowItems ?: 4
    val t9SidebarSymbols: List<String> = selfConfig?.t9SidebarSymbols ?: emptyList()

    val t9SideFont: List<String>
        get() = theme.generalStyle.fonts.t9_side.ifEmpty { theme.generalStyle.fonts.key }
    val t9SideTextSize: Float
        get() {
            val v = theme.generalStyle.fonts.t9_side_size
            return if (v > 0f) v else theme.generalStyle.fonts.key_size
        }
    val t9SideRoundCorner: Float
        get() {
            val v = theme.generalStyle.t9SideRoundCorner
            return if (v >= 0f) v else roundCorner
        }

    val asciiKeyboard: String? = selfConfig?.asciiKeyboard // 英文鍵盤

    fun recalculateLayout(width: Int) {
        if (width <= 0 || mKeys.isEmpty()) return
        val scaleX = width.toFloat() / allowedWidth.toFloat()
        minWidth = (minWidth * scaleX).toInt()
        var yPos = 0f
        for (rowIdx in mKeys.map { it.row }.distinct().sorted()) {
            val rowKeys = mKeys.filter { it.row == rowIdx }
            var xPos = 0f
            for (key in rowKeys) {
                key.x = (key.x * scaleX).toInt()
                key.y = yPos.toInt()
                key.width = (key.width * scaleX).toInt()
                xPos = (key.x + key.width).toFloat()
            }
            yPos += rowKeys.first().height
        }
    }

    fun getT9SidebarHeight(): Int {
        val spanRow = t9SidebarSpanRows
        val firstKeyInNextRow = mKeys.firstOrNull { it.row >= spanRow }
        return firstKeyInNextRow?.y ?: height
    }

    val keyboardHeight: Int =
        intArrayOf(
            selfConfig?.let { getKeyboardHeightFromKeyboardConfig(it) } ?: 0,
            getKeyboardHeightFromTheme(theme),
        ).firstOrNull { it > 0 } ?: 0

    private val expandKeypressArea: Boolean by AppPrefs.defaultInstance().keyboard.expandKeypressArea

    init {
        run {
            if (selfConfig == null) return@run
            val rows = selfConfig.rows
            if (rows.isEmpty()) {
                Timber.w("Keyboard has no rows")
                return@run
            }

            fun firstNonZero(a: Float, b: Float, c: Float): Float = if (a != 0f) {
                a
            } else if (b != 0f) {
                b
            } else {
                c
            }

            val rowCount = rows.size
            val isSplit = context.isLandscapeMode() && landscapePercent > 0
            val splitRatio = if (isSplit) landscapePercent / 100f else 0f

            // ------ row height weight distribution ------
            val rawDefinedHeightSum = rows.sumOf { it.height.toDouble() }.toFloat()
            if (rawDefinedHeightSum - 1.0f > 0.0001f) {
                Timber.e("Row height weights sum to %.3f, must be <= 1.0", rawDefinedHeightSum)
                return@run
            }
            val definedHeightSum = rawDefinedHeightSum.coerceAtMost(1.0f)

            val undefinedHeightCount = rows.count { it.height == 0f }
            val perUndefinedHeight = if (undefinedHeightCount > 0) {
                (1.0f - definedHeightSum) / undefinedHeightCount
            } else {
                0f
            }

            val rowHeightWeights = rows.map { row ->
                if (row.height > 0f) row.height else perUndefinedHeight
            }
            val totalRowHeightWeight = rowHeightWeights.sum()
            val topMarginWeight = if (totalRowHeightWeight < 1.0f) {
                (1.0f - totalRowHeightWeight) / 2f
            } else {
                0f
            }

            val availableHeight = keyboardHeight
            val topMarginPx = (topMarginWeight * availableHeight).toInt()
            val bottomMarginPx = (topMarginWeight * availableHeight).toInt()

            val rowHeightsPx = rowHeightWeights.map { (it * availableHeight).toInt() }

            // key area pixel width (accounts for split ratio)
            val keyAreaWidthPx = (allowedWidth.toFloat() / (1f + splitRatio)).toInt()
            val oneWeightWidthPx = if (splitRatio > 0f) {
                allowedWidth.toFloat() / (1f + splitRatio)
            } else {
                allowedWidth.toFloat()
            }

            // ------ per-row key width distribution and Key creation ------
            val spacers = mutableListOf<Triple<Int, Int, Int>>()
            var yPos = topMarginPx

            for (rowIdx in 0 until rowCount) {
                val row = rows[rowIdx]
                val currentRowHeight = rowHeightsPx[rowIdx]
                val keys = row.keys

                if (keys.isEmpty()) {
                    yPos += currentRowHeight
                    continue
                }

                // determine key weights for this row
                val keyCount = keys.size
                val keyWeights = FloatArray(keyCount)
                var definedWidthSum = 0f

                for (i in 0 until keyCount) {
                    val key = keys[i]
                    if (key.width > 0f) {
                        keyWeights[i] = key.width
                        definedWidthSum += key.width
                    }
                }

                if (definedWidthSum - 1.0f > 0.0001f) {
                    Timber.e("Row %d: key width weights sum to %.3f, must be <= 1.0", rowIdx, definedWidthSum)
                    return@run
                }
                definedWidthSum = definedWidthSum.coerceAtMost(1.0f)

                val hasUndefinedKeys = keys.any { it.width == 0f }
                val isAllDefined = !hasUndefinedKeys

                var totalKeyWeight: Float
                var leftMarginWeight: Float

                if (isAllDefined) {
                    totalKeyWeight = definedWidthSum
                    leftMarginWeight = if (totalKeyWeight < 1.0f) (1.0f - totalKeyWeight) / 2f else 0f
                } else {
                    // distribute remaining weight to undefined keys
                    totalKeyWeight = 1.0f
                    leftMarginWeight = 0f
                    val perUndefinedWidth = (1.0f - definedWidthSum) / keys.count { it.width == 0f }
                    for (i in 0 until keyCount) {
                        if (keyWeights[i] == 0f) {
                            keyWeights[i] = perUndefinedWidth
                        }
                    }
                }

                var xPos = (leftMarginWeight * allowedWidth).toInt()

                // split keyboard tracking
                var splitInserted = isSplit && row.split
                var rowWeightAccumulated = 0f

                var column = 0

                for (i in 0 until keyCount) {
                    val textKey = keys[i]
                    val weight = keyWeights[i]
                    rowWeightAccumulated += weight

                    // process split gap
                    if (isSplit && !splitInserted && rowWeightAccumulated > totalKeyWeight * 0.5f) {
                        splitInserted = true
                        val gap = (totalKeyWeight * splitRatio * oneWeightWidthPx).toInt()
                        if (weight > 0.2f) {
                            // large keys absorb the gap
                        } else {
                            if (expandKeypressArea) spacers.add(Triple(xPos, gap, rowIdx))
                            xPos += gap
                        }
                    }

                    if (textKey.spacer) {
                        val widthPx = (weight * keyAreaWidthPx).toInt()
                        if (expandKeypressArea) spacers.add(Triple(xPos, widthPx, rowIdx))
                        xPos += widthPx
                        column++
                        continue
                    }

                    var widthPx = (weight * keyAreaWidthPx).toInt()

                    // apply large-key gap absorption for split
                    if (isSplit && !splitInserted && rowWeightAccumulated > totalKeyWeight * 0.5f) {
                        // the key that triggered the split: absorb the gap
                        val gap = (totalKeyWeight * splitRatio * oneWeightWidthPx).toInt()
                        widthPx += gap
                        splitInserted = true // prevent double-insertion
                    }

                    val key = Key(this, textKey)

                    key.keyTextOffsetX = firstNonZero(textKey.keyTextOffsetX, selfConfig.keyTextOffsetX, theme.generalStyle.keyTextOffsetX)
                    key.keyTextOffsetY = firstNonZero(textKey.keyTextOffsetY, selfConfig.keyTextOffsetY, theme.generalStyle.keyTextOffsetY)
                    key.keySymbolOffsetX = firstNonZero(textKey.keySymbolOffsetX, selfConfig.keySymbolOffsetX, theme.generalStyle.keySymbolOffsetX)
                    key.keySymbolOffsetY = firstNonZero(textKey.keySymbolOffsetY, selfConfig.keySymbolOffsetY, theme.generalStyle.keySymbolOffsetY)
                    key.keyHintOffsetX = firstNonZero(textKey.keyHintOffsetX, selfConfig.keyHintOffsetX, theme.generalStyle.keyHintOffsetX)
                    key.keyHintOffsetY = firstNonZero(textKey.keyHintOffsetY, selfConfig.keyHintOffsetY, theme.generalStyle.keyHintOffsetY)
                    key.keyPressOffsetX = firstNonZero(textKey.keyPressOffsetX, selfConfig.keyPressOffsetX, theme.generalStyle.keyPressOffsetX)
                    key.keyPressOffsetY = firstNonZero(textKey.keyPressOffsetY, selfConfig.keyPressOffsetY, theme.generalStyle.keyPressOffsetY)

                    key.x = xPos
                    key.y = yPos

                    // correct minor rounding errors on the right edge
                    val rightGap = abs(allowedWidth - xPos - widthPx)
                    key.width = if (rightGap <= allowedWidth / 100) allowedWidth - xPos else widthPx

                    key.height = currentRowHeight
                    key.row = rowIdx
                    key.column = column

                    column++
                    xPos += key.width

                    mKeys.add(key)

                    if (xPos > minWidth) {
                        minWidth = xPos
                    }
                }

                yPos += currentRowHeight
            }

            // Expand keypress area to edge by distributing spacer widths to neighbors
            if (expandKeypressArea && spacers.isNotEmpty()) {
                for ((spacerX, spacerWidth, spacerRow) in spacers) {
                    val (leftKeys, rightKeys) = mKeys.filter { it.row == spacerRow }.partition { it.x + it.width <= spacerX }
                    val leftKey = leftKeys.maxByOrNull { it.x }
                    val rightKey = rightKeys.minByOrNull { it.x }
                    when {
                        leftKey != null && rightKey != null -> {
                            leftKey.extraWidthRight += spacerWidth / 2
                            rightKey.extraWidthLeft += spacerWidth - spacerWidth / 2
                        }
                        leftKey != null -> leftKey.extraWidthRight += spacerWidth
                        rightKey != null -> rightKey.extraWidthLeft += spacerWidth
                    }
                }
            }

            mKeys.forEachIndexed { index, key ->
                val isLastInRow = index == mKeys.size - 1 || mKeys[index + 1].row != key.row
                if (isLastInRow) {
                    key.edgeFlags = key.edgeFlags or EDGE_RIGHT
                }
            }

            height = yPos + bottomMarginPx

            mKeys.forEachIndexed { index, key ->
                key.index = index
                if (key.column == 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                if (key.row == 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                if (key.row == rowCount - 1) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
            }
        }
    }

    private fun getKeyboardHeightFromTheme(theme: Theme): Int {
        var keyboardHeight = theme.generalStyle.keyboardHeight
        if (context.isLandscapeMode()) {
            val keyboardHeightLand = theme.generalStyle.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return context.dp(keyboardHeight)
    }

    private fun getKeyboardHeightFromKeyboardConfig(textKeyboard: TextKeyboard): Int {
        var keyboardHeight = textKeyboard.keyboardHeight
        if (context.isLandscapeMode()) {
            val keyboardHeightLand = textKeyboard.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return context.dp(keyboardHeight)
    }

    fun setModifierKey(
        c: Int,
        key: Key?,
    ) {
        when (c) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                mShiftKey = key
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                mCtrlKey = key
            }
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                mMetaKey = key
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                mAltKey = key
            }
            KeyEvent.KEYCODE_SYM -> {
                mSymKey = key
            }
        }
    }

    fun invalidateAllKeyColors() {
        mKeys.forEach { it.invalidateColors() }
    }

    fun refreshKeyBehaviors(newConfig: TextKeyboard) {
        labelTransform = newConfig.labelTransform
        val allNewKeys = newConfig.rows.flatMap { it.keys }.filter { !it.spacer }
        for ((keyIndex, newTextKey) in allNewKeys.withIndex()) {
            mKeys.getOrNull(keyIndex)?.refreshFromConfig(newTextKey, newConfig, theme.generalStyle)
        }
    }

    val keys: List<Key>
        get() = mKeys

    private fun setModifier(
        mask: Int,
        value: Boolean,
    ): Boolean {
        if (modifier.hasFlag(mask) == value) return false
        modifier = if (value) modifier or mask else modifier and mask.inv()
        return true
    }

    val isShifted: Boolean
        get() = modifier.hasFlag(KeyEvent.META_SHIFT_ON) || mShiftKey?.isOn == true

    val isOnlyShiftOn: Boolean
        get() =
            isShifted &&
                !modifier.hasFlag(KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON)

    /**
     * 设置Shift键状态（用于自动大写）
     *
     * @param on 是否锁定Shift键
     * @param shifted 是否按下Shift键
     * @return Shift键状态是否改变
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        mShiftKey?.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, shifted)
    }

    /**
     * 设置修饰键的状态
     *
     * @param on 是否锁定修饰键
     * @param keycode 修饰键的 KeyEvent 掩码
     * @return 修饰键状态是否改变
     */
    fun clickModifierKey(
        on: Boolean,
        keycode: Int,
    ): Boolean {
        val keyDown = !modifier.hasFlag(keycode)
        val modifierKey =
            when (keycode) {
                KeyEvent.META_SHIFT_ON -> mShiftKey
                KeyEvent.META_ALT_ON -> mAltKey
                KeyEvent.META_CTRL_ON -> mCtrlKey
                KeyEvent.META_META_ON -> mMetaKey
                KeyEvent.KEYCODE_SYM -> mSymKey
                else -> null
            }
        val keepOn = modifierKey?.setOn(on) ?: on
        return if (on) setModifier(keycode, keepOn) else setModifier(keycode, keyDown)
    }

    fun refreshModifier(): Boolean {
        // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
        var result = false
        if (mShiftKey != null && !mShiftKey!!.isOn) result = result || setModifier(KeyEvent.META_SHIFT_ON, false)
        if (mAltKey != null && !mAltKey!!.isOn) result = result || setModifier(KeyEvent.META_ALT_ON, false)
        if (mCtrlKey != null && !mCtrlKey!!.isOn) result = result || setModifier(KeyEvent.META_CTRL_ON, false)
        if (mMetaKey != null && !mMetaKey!!.isOn) result = result || setModifier(KeyEvent.META_META_ON, false)
        if (mSymKey != null && !mSymKey!!.isOn) result = result || setModifier(KeyEvent.KEYCODE_SYM, false)
        return result
    }

    private fun computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (minWidth + GRID_WIDTH - 1) / GRID_WIDTH
        mCellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        gridNeighbors = arrayOfNulls(GRID_SIZE)
        val indices = IntArray(mKeys.size)
        val gridWidth = GRID_WIDTH * mCellWidth
        val gridHeight = GRID_HEIGHT * mCellHeight
        var x = 0
        while (x < gridWidth) {
            var y = 0
            while (y < gridHeight) {
                var count = 0
                for (i in mKeys.indices) {
                    val key = mKeys[i]
                    if (key.squaredDistanceFrom(x, y) < proximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < proximityThreshold ||
                        (
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < proximityThreshold
                            ) ||
                        key.squaredDistanceFrom(x, y + mCellHeight - 1) < proximityThreshold ||
                        key.isInside(x, y) ||
                        key.isInside(x + mCellWidth - 1, y) ||
                        key.isInside(x + mCellWidth - 1, y + mCellHeight - 1) ||
                        key.isInside(x, y + mCellHeight - 1)
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                gridNeighbors?.set(y / mCellHeight * GRID_WIDTH + x / mCellWidth, cell)
                y += mCellHeight
            }
            x += mCellWidth
        }
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(
        x: Int,
        y: Int,
    ): IntArray? {
        if (gridNeighbors == null) computeNearestNeighbors()
        if (x in 0 until minWidth && y in 0 until height) {
            val index = y / mCellHeight * GRID_WIDTH + x / mCellWidth
            if (index < GRID_SIZE) {
                return gridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    val isLabelUppercase: Boolean
        get() = labelTransform == TextKeyboard.LabelTransform.UPPERCASE

    companion object {
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
        private const val GRID_WIDTH = 10
        private const val GRID_HEIGHT = 5
        private const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

        /** Number of key widths from current touch point to search for nearest keys.  */
        const val SEARCH_DISTANCE = 1.4f

        fun shadowDirectionToOffset(dir: List<String>, offset: Float): Pair<Float, Float> {
            var dx = 0f
            var dy = 0f
            for (d in dir) {
                when (d.lowercase()) {
                    "left" -> dx -= 1f
                    "right" -> dx += 1f
                    "up" -> dy -= 1f
                    "down" -> dy += 1f
                }
            }
            val mag = sqrt(dx * dx + dy * dy)
            if (mag > 0f) {
                dx = dx / mag * offset
                dy = dy / mag * offset
            }
            return Pair(dx, dy)
        }
    }
}
