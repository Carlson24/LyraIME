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
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.isLandscape
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.systemservices.windowManager

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

    /** 默認行距  */
    internal val verticalGap: Int =
        intArrayOf(
            selfConfig?.verticalGap ?: 0,
            theme.generalStyle.verticalGap,
        ).firstOrNull { it > 0 }?.let { context.dp(it) } ?: 0

    /** 默認按鍵圓角半徑  */
    val roundCorner: Float =
        selfConfig?.roundCorner?.takeIf { it >= 0f } ?: theme.generalStyle.roundCorner

    /** 默認按鍵邊框寬度  */
    val keyBorder: Int =
        selfConfig?.keyBorder?.takeIf { it >= 0 } ?: theme.generalStyle.keyBorder

    /** 鍵盤的Shift鍵  */
    var mShiftKey: Key? = null
    var mCtrlKey: Key? = null
    var mAltKey: Key? = null
    var mMetaKey: Key? = null
    var mSymKey: Key? = null

    var height = 0
        private set

    var minWidth = 0
        private set

    /** List of keys in this keyboard  */
    private val mKeys = mutableListOf<Key>()
    val appearanceStateKeys = mutableListOf<Key>()
    var modifier = 0
        private set

    var firstPressedKeyIndex: Int = -1

    /** Row layout data for view construction  */
    data class RowLayout(
        val entries: List<RowLayoutEntry>,
        val pixelHeight: Int,
    )

    sealed interface RowLayoutEntry {
        data class KeyRef(val key: Key, val weight: Float) : RowLayoutEntry
        data class Spacer(val weight: Float) : RowLayoutEntry
    }

    private val _rowLayouts = mutableListOf<RowLayout>()
    val rowLayouts: List<RowLayout>
        get() = _rowLayouts

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

            val isOneHandMode = runCatching {
                RimeDaemon.getFirstSessionOrNull()?.run { getRuntimeOption("_one_hand_mode") }
            }.getOrNull() == true

            fun resolvePadding(configValue: Int) = configValue.takeIf { it > 0 } ?: padding

            val totalPadding = if (isOneHandMode && isPortrait) {
                resolvePadding(theme.generalStyle.keyboardPaddingLeft) +
                    resolvePadding(theme.generalStyle.keyboardPaddingRight)
            } else {
                2 * padding
            }

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

    private val labelTransform = selfConfig?.labelTransform ?: TextKeyboard.LabelTransform.NONE
    val isLock = selfConfig?.lock ?: false
    val asciiKeyboard: String? = selfConfig?.asciiKeyboard

    val keyboardHeight: Int =
        intArrayOf(
            selfConfig?.let { getKeyboardHeightFromKeyboardConfig(it) } ?: 0,
            getKeyboardHeightFromTheme(theme),
        ).firstOrNull { it > 0 } ?: 0

    init {
        if (selfConfig != null && selfConfig.rows.isNotEmpty()) {
            val rows = selfConfig.rows

            val isSplit = context.isLandscapeMode() && landscapePercent > 0
            val splitRatio = landscapePercent / 100f

            val totalHeightWeight = rows.sumOf { it.height.toDouble() }.toFloat()
            val allowedW = allowedWidth.toFloat()

            var yPos = 0

            for ((rowIdx, rowConfig) in rows.withIndex()) {
                val rowPixelHeight = (rowConfig.height / totalHeightWeight * keyboardHeight).toInt()

                val layoutEntries = mutableListOf<RowLayoutEntry>()
                var column = 0
                var xPos = 0

                val baseEntries = rowConfig.entries
                val entriesWithSplit = mutableListOf<Pair<Float, TextKeyboard.RowEntry>>()
                for ((i, entry) in baseEntries.withIndex()) {
                    if (isSplit && rowConfig.splitAfter == i) {
                        val totalRowWeight = baseEntries.sumOf { entryWeight(it) }
                        val gapWeight = totalRowWeight * splitRatio
                        entriesWithSplit.add(gapWeight.toFloat() to TextKeyboard.RowEntry.Spacer(gapWeight.toFloat()))
                    }
                    entriesWithSplit.add(entryWeight(entry).toFloat() to entry)
                }

                val totalRowWeight = entriesWithSplit.sumOf { it.first.toDouble() }.toFloat()

                for ((entryWeight, rowEntry) in entriesWithSplit) {
                    val pixelWidth = (entryWeight / totalRowWeight * allowedW).toInt()

                    when (rowEntry) {
                        is TextKeyboard.RowEntry.Key -> {
                            val cfg = rowEntry.config
                            val key = Key(this, cfg)

                            key.keyTextOffsetX = firstNonZero(
                                cfg.keyTextOffsetX, selfConfig.keyTextOffsetX, theme.generalStyle.keyTextOffsetX,
                            )
                            key.keyTextOffsetY = firstNonZero(
                                cfg.keyTextOffsetY, selfConfig.keyTextOffsetY, theme.generalStyle.keyTextOffsetY,
                            )
                            key.keySymbolOffsetX = firstNonZero(
                                cfg.keySymbolOffsetX, selfConfig.keySymbolOffsetX, theme.generalStyle.keySymbolOffsetX,
                            )
                            key.keySymbolOffsetY = firstNonZero(
                                cfg.keySymbolOffsetY, selfConfig.keySymbolOffsetY, theme.generalStyle.keySymbolOffsetY,
                            )
                            key.keyHintOffsetX = firstNonZero(
                                cfg.keyHintOffsetX, selfConfig.keyHintOffsetX, theme.generalStyle.keyHintOffsetX,
                            )
                            key.keyHintOffsetY = firstNonZero(
                                cfg.keyHintOffsetY, selfConfig.keyHintOffsetY, theme.generalStyle.keyHintOffsetY,
                            )
                            key.keyPressOffsetX = firstNonZero(
                                cfg.keyPressOffsetX, selfConfig.keyPressOffsetX, theme.generalStyle.keyPressOffsetX,
                            )
                            key.keyPressOffsetY = firstNonZero(
                                cfg.keyPressOffsetY, selfConfig.keyPressOffsetY, theme.generalStyle.keyPressOffsetY,
                            )

                            key.x = xPos
                            key.y = yPos
                            key.width = pixelWidth
                            key.height = rowPixelHeight
                            key.row = rowIdx
                            key.column = column

                            layoutEntries.add(RowLayoutEntry.KeyRef(key, entryWeight))
                            mKeys.add(key)
                            column++
                        }
                        is TextKeyboard.RowEntry.Spacer -> {
                            layoutEntries.add(RowLayoutEntry.Spacer(entryWeight))
                        }
                    }

                    xPos += pixelWidth
                }

                _rowLayouts.add(RowLayout(entries = layoutEntries, pixelHeight = rowPixelHeight))
                yPos += rowPixelHeight
            }

            mKeys.lastOrNull()?.edgeFlags = mKeys.lastOrNull()?.edgeFlags?.or(EDGE_RIGHT) ?: 0

            height = yPos
            minWidth = allowedW.toInt()

            mKeys.forEachIndexed { index, key ->
                key.index = index
                if (key.column == 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                if (key.row == 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                if (key.row == rows.size - 1) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
            }
        }
    }

    private fun entryWeight(entry: TextKeyboard.RowEntry): Double = when (entry) {
        is TextKeyboard.RowEntry.Key -> entry.config.weight.toDouble()
        is TextKeyboard.RowEntry.Spacer -> entry.weight.toDouble()
    }

    private fun firstNonZero(a: Float, b: Float, c: Float): Float = if (a != 0f) {
        a
    } else if (b != 0f) {
        b
    } else {
        c
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

    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        mShiftKey?.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, shifted)
    }

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
        var result = false
        if (mShiftKey != null && !mShiftKey!!.isOn) result = result || setModifier(KeyEvent.META_SHIFT_ON, false)
        if (mAltKey != null && !mAltKey!!.isOn) result = result || setModifier(KeyEvent.META_ALT_ON, false)
        if (mCtrlKey != null && !mCtrlKey!!.isOn) result = result || setModifier(KeyEvent.META_CTRL_ON, false)
        if (mMetaKey != null && !mMetaKey!!.isOn) result = result || setModifier(KeyEvent.META_META_ON, false)
        if (mSymKey != null && !mSymKey!!.isOn) result = result || setModifier(KeyEvent.KEYCODE_SYM, false)
        return result
    }

    val isLabelUppercase: Boolean
        get() = labelTransform == TextKeyboard.LabelTransform.UPPERCASE

    companion object {
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
    }
}
