// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import androidx.annotation.ColorInt
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.ResettableLazy
import splitties.bitflags.hasFlag

/** [鍵盤][Keyboard]中的各個按鍵，包含單擊、長按、滑動等多種[事件][KeyAction]  */
class Key(
    private val parent: Keyboard,
    initialConfig: TextKeyboard.TextKey? = null,
) {
    private var selfConfig: TextKeyboard.TextKey? = initialConfig

    var index: Int = -1

    var keyActions: Map<KeyBehavior, KeyAction> =
        buildMap {
            selfConfig?.behaviors?.forEach {
                put(it.key, KeyActionManager.getAction(it.value))
            }
            val clickAction = selfConfig?.click?.takeIf { it.isNotEmpty() }?.let {
                KeyActionManager.getAction(it)
            }
            if (clickAction != null) {
                put(KeyBehavior.CLICK, clickAction)
            }
        }
        private set
    var edgeFlags = 0
    private var sendBindings: Boolean

    var isPressed = false
        private set
    var isOn = false
        private set

    var x = 0
    var y = 0

    var width = 0
    var height = 0
    var gap = 0
    var row = 0
    var column = 0

    var extraWidthLeft = 0
    var extraWidthRight = 0

    var label = selfConfig?.label ?: emptyList()
        private set
    var asciiLabel = selfConfig?.asciiLabel ?: emptyList()
        private set
    var labelSymbol = selfConfig?.labelSymbol ?: emptyList()
        private set
    var hint: List<TextKeyboard.LabelSegment> = selfConfig?.hint ?: emptyList()
        private set
    var dynamicTarget: String? = selfConfig?.dynamic?.takeIf { it.isNotEmpty() }
        private set
    var popup = selfConfig?.popup ?: emptyList()
        private set

    var keyTextSize: Float = selfConfig?.keyTextSize ?: 0f
        private set
    var symbolTextSize: Float = selfConfig?.symbolTextSize ?: 0f
        private set
    var hintTextSize: Float = selfConfig?.hintTextSize ?: 0f
        private set
    var roundCorner: Float? = selfConfig?.roundCorner?.takeIf { it >= 0 }
        private set
    var roundedCornerTopLeft: Float? = selfConfig?.roundedCornerTopLeft
        private set
    var roundedCornerTopRight: Float? = selfConfig?.roundedCornerTopRight
        private set
    var roundedCornerBottomLeft: Float? = selfConfig?.roundedCornerBottomLeft
        private set
    var roundedCornerBottomRight: Float? = selfConfig?.roundedCornerBottomRight
        private set
    var keyBorder: Int? = selfConfig?.keyBorder?.takeIf { it >= 0 }
        private set
    var keyBorderColor: String? = selfConfig?.keyBorderColor?.takeIf { it.isNotEmpty() }
        private set
    var keyTextOffsetX = 0f
        get() = field + keyOffsetX
    var keyTextOffsetY = 0f
        get() = field + keyOffsetY
    var keySymbolOffsetX = 0f
        get() = field + keyOffsetX
    var keySymbolOffsetY = 0f
        get() = field + keyOffsetY
    var keyHintOffsetX = 0f
        get() = field + keyOffsetX
    var keyHintOffsetY = 0f
        get() = field + keyOffsetY
    var keyPressOffsetX = 0f
    var keyPressOffsetY = 0f

    // get color from key customization or just fallback to specified color
    private fun getColor(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ): Int = selfConfig?.let {
        runCatching { ColorManager.getColor(src(it)) }.getOrNull()
    } ?: ColorManager.getColor(fallback)

    // get color from common color schemes or just fallback to default color
    private fun getColor(
        key: String,
        @ColorInt default: Int,
    ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(default)

    private fun getDrawable(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ) = selfConfig?.let {
        if (src(it).isEmpty()) null else ColorManager.getDrawable(src(it))
    } ?: ColorManager.getDrawable(fallback)

    private val keyBackgroundDelegate = ResettableLazy { getDrawable({ keyBackColor }, "key_back_color") }
    private val keyBackground: Drawable? by keyBackgroundDelegate
    private val offKeyBackgroundDelegate = ResettableLazy { ColorManager.getDrawable("off_key_back_color") }
    private val offKeyBackground: Drawable? by offKeyBackgroundDelegate
    private val onKeyBackgroundDelegate = ResettableLazy { ColorManager.getDrawable("on_key_back_color") }
    private val onKeyBackground: Drawable? by onKeyBackgroundDelegate

    private val keyTextColorDelegate = ResettableLazy { getColor({ keyTextColor }, "key_text_color") }
    private val keyTextColor: Int by keyTextColorDelegate
    private val offKeyTextColorDelegate = ResettableLazy { getColor("off_key_text_color", keyTextColor) }
    private val offKeyTextColor: Int by offKeyTextColorDelegate
    private val onKeyTextColorDelegate = ResettableLazy { getColor("on_key_text_color", keyTextColor) }
    private val onKeyTextColor: Int by onKeyTextColorDelegate
    private val keySymbolColorDelegate = ResettableLazy { getColor({ keySymbolColor }, "key_symbol_color") }
    private val keySymbolColor: Int by keySymbolColorDelegate
    private val offKeySymbolColorDelegate = ResettableLazy { getColor("off_key_symbol_color", keySymbolColor) }
    private val offKeySymbolColor: Int by offKeySymbolColorDelegate
    private val onKeySymbolColorDelegate = ResettableLazy { getColor("on_key_symbol_color", keySymbolColor) }
    private val onKeySymbolColor: Int by onKeySymbolColorDelegate
    private val hlKeyBackgroundDelegate = ResettableLazy { getDrawable({ hlKeyBackColor }, "hilited_key_back_color") }
    private val hlKeyBackground: Drawable? by hlKeyBackgroundDelegate
    private val hlOffKeyBackgroundDelegate = ResettableLazy { ColorManager.getDrawable("hilited_off_key_back_color") }
    private val hlOffKeyBackground: Drawable? by hlOffKeyBackgroundDelegate
    private val hlOnKeyBackgroundDelegate = ResettableLazy { ColorManager.getDrawable("hilited_on_key_back_color") }
    private val hlOnKeyBackground: Drawable? by hlOnKeyBackgroundDelegate
    private val hlKeyTextColorDelegate = ResettableLazy { getColor({ hlKeyTextColor }, "hilited_key_text_color") }
    private val hlKeyTextColor: Int by hlKeyTextColorDelegate
    private val hlOffKeyTextColorDelegate = ResettableLazy { getColor("hilited_off_key_text_color", hlKeyTextColor) }
    private val hlOffKeyTextColor: Int by hlOffKeyTextColorDelegate
    private val hlOnKeyTextColorDelegate = ResettableLazy { getColor("hilited_on_key_text_color", hlKeyTextColor) }
    private val hlOnKeyTextColor: Int by hlOnKeyTextColorDelegate
    private val hlKeySymbolColorDelegate = ResettableLazy { getColor({ hlKeySymbolColor }, "hilited_key_symbol_color") }
    private val hlKeySymbolColor: Int by hlKeySymbolColorDelegate
    private val hlOffKeySymbolColorDelegate = ResettableLazy { getColor("hilited_off_key_symbol_color", hlKeySymbolColor) }
    private val hlOffKeySymbolColor: Int by hlOffKeySymbolColorDelegate
    private val hlOnKeySymbolColorDelegate = ResettableLazy { getColor("hilited_on_key_symbol_color", hlKeySymbolColor) }
    private val hlOnKeySymbolColor: Int by hlOnKeySymbolColorDelegate

    private val keyBorderColorDelegate = ResettableLazy {
        val perKey = keyBorderColor
        if (perKey != null) {
            runCatching { ColorManager.getColor(perKey) }.getOrDefault(ColorManager.getColor("key_border_color"))
        } else {
            ColorManager.getColor("key_border_color")
        }
    }
    val keyBorderColorValue: Int by keyBorderColorDelegate

    fun invalidateColors() {
        keyBackgroundDelegate.invalidate()
        offKeyBackgroundDelegate.invalidate()
        onKeyBackgroundDelegate.invalidate()
        keyTextColorDelegate.invalidate()
        offKeyTextColorDelegate.invalidate()
        onKeyTextColorDelegate.invalidate()
        keySymbolColorDelegate.invalidate()
        offKeySymbolColorDelegate.invalidate()
        onKeySymbolColorDelegate.invalidate()
        hlKeyBackgroundDelegate.invalidate()
        hlOffKeyBackgroundDelegate.invalidate()
        hlOnKeyBackgroundDelegate.invalidate()
        hlKeyTextColorDelegate.invalidate()
        hlOffKeyTextColorDelegate.invalidate()
        hlOnKeyTextColorDelegate.invalidate()
        hlKeySymbolColorDelegate.invalidate()
        hlOffKeySymbolColorDelegate.invalidate()
        hlOnKeySymbolColorDelegate.invalidate()
        keyBorderColorDelegate.invalidate()
    }

    fun firstNonZero(a: Float, b: Float, c: Float): Float = if (a != 0f) {
        a
    } else if (b != 0f) {
        b
    } else {
        c
    }

    fun refreshFromConfig(
        newConfig: TextKeyboard.TextKey,
        keyboardConfig: TextKeyboard,
        themeGeneralStyle: GeneralStyle,
    ) {
        selfConfig = newConfig
        label = newConfig.label
        asciiLabel = newConfig.asciiLabel
        labelSymbol = newConfig.labelSymbol
        hint = newConfig.hint
        dynamicTarget = newConfig.dynamic.takeIf { it.isNotEmpty() }
        popup = newConfig.popup
        keyTextSize = newConfig.keyTextSize
        symbolTextSize = newConfig.symbolTextSize
        hintTextSize = newConfig.hintTextSize
        roundCorner = newConfig.roundCorner.takeIf { it >= 0f }
        roundedCornerTopLeft = newConfig.roundedCornerTopLeft
        roundedCornerTopRight = newConfig.roundedCornerTopRight
        roundedCornerBottomLeft = newConfig.roundedCornerBottomLeft
        roundedCornerBottomRight = newConfig.roundedCornerBottomRight
        keyBorder = newConfig.keyBorder.takeIf { it >= 0 }
        keyBorderColor = newConfig.keyBorderColor.takeIf { it.isNotEmpty() }
        keyActions = buildMap {
            newConfig.behaviors.forEach {
                put(it.key, KeyActionManager.getAction(it.value))
            }
            val clickAction = newConfig.click.takeIf { it.isNotEmpty() }?.let {
                KeyActionManager.getAction(it)
            }
            if (clickAction != null) {
                put(KeyBehavior.CLICK, clickAction)
            }
        }
        val hasStateDependentBehavior = newConfig.behaviors.keys.any { it < KeyBehavior.COMBO }
        sendBindings = newConfig.sendBindings || hasStateDependentBehavior

        keyTextOffsetX = firstNonZero(newConfig.keyTextOffsetX, keyboardConfig.keyTextOffsetX, themeGeneralStyle.keyTextOffsetX)
        keyTextOffsetY = firstNonZero(newConfig.keyTextOffsetY, keyboardConfig.keyTextOffsetY, themeGeneralStyle.keyTextOffsetY)
        keySymbolOffsetX = firstNonZero(newConfig.keySymbolOffsetX, keyboardConfig.keySymbolOffsetX, themeGeneralStyle.keySymbolOffsetX)
        keySymbolOffsetY = firstNonZero(newConfig.keySymbolOffsetY, keyboardConfig.keySymbolOffsetY, themeGeneralStyle.keySymbolOffsetY)
        keyHintOffsetX = firstNonZero(newConfig.keyHintOffsetX, keyboardConfig.keyHintOffsetX, themeGeneralStyle.keyHintOffsetX)
        keyHintOffsetY = firstNonZero(newConfig.keyHintOffsetY, keyboardConfig.keyHintOffsetY, themeGeneralStyle.keyHintOffsetY)
        keyPressOffsetX = firstNonZero(newConfig.keyPressOffsetX, keyboardConfig.keyPressOffsetX, themeGeneralStyle.keyPressOffsetX)
        keyPressOffsetY = firstNonZero(newConfig.keyPressOffsetY, keyboardConfig.keyPressOffsetY, themeGeneralStyle.keyPressOffsetY)

        invalidateColors()
    }

    init {
        val config = selfConfig
        if (config != null) {
            val hasStateDependentBehavior = config.behaviors.keys.any { it < KeyBehavior.COMBO }
            if (hasStateDependentBehavior) parent.appearanceStateKeys.add(this)
            sendBindings = config.sendBindings || hasStateDependentBehavior
        } else {
            sendBindings = true
        }
        parent.setModifierKey(this.code, this)
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    private val keyOffsetX: Float
        get() = if (isPressed) keyPressOffsetX else 0f
    private val keyOffsetY: Float
        get() = if (isPressed) keyPressOffsetY else 0f

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or state.
     *
     * @see .onReleased
     */
    fun onPressed() {
        isPressed = true
    }

    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
     * state of the key if the finger was release inside.
     *
     * @see .onPressed
     */
    fun onReleased() {
        isPressed = false
        if (click!!.isSticky) isOn = !isOn
    }

    /**
     * Detects if a point falls inside this key.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls inside the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be inside the
     * key.
     */
    fun isInside(
        x: Int,
        y: Int,
    ): Boolean {
        val leftEdge = edgeFlags and Keyboard.EDGE_LEFT > 0
        val rightEdge = edgeFlags and Keyboard.EDGE_RIGHT > 0
        val topEdge = edgeFlags and Keyboard.EDGE_TOP > 0
        val bottomEdge = edgeFlags and Keyboard.EDGE_BOTTOM > 0
        return (
            (x >= this.x || leftEdge && x <= this.x + width) &&
                (x < this.x + width || rightEdge && x >= this.x) &&
                (y >= this.y || topEdge && y <= this.y + height) &&
                (y < this.y + height || bottomEdge && y >= this.y)
            )
    }

    /**
     * Returns the square of the distance between the center of the key and the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the center of the key
     */
    fun squaredDistanceFrom(
        x: Int,
        y: Int,
    ): Int {
        val xDist = this.x + width / 2 - x
        val yDist = this.y + height / 2 - y
        return xDist * xDist + yDist * yDist
    }

    val isShift: Boolean
        get() = this.code == KeyEvent.KEYCODE_SHIFT_LEFT || this.code == KeyEvent.KEYCODE_SHIFT_RIGHT

    /**
     * @param behavior 同文按键模式（点击/长按/滑动）
     * @return
     */
    fun sendBindings(behavior: KeyBehavior): Boolean = keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } != null || checkKeyAction(sendBindings) != null

    private val keyAction: KeyAction?
        get() = checkKeyAction() ?: click

    val click: KeyAction?
        get() = keyActions[KeyBehavior.CLICK]
    val longClick: KeyAction?
        get() = keyActions[KeyBehavior.LONG_CLICK]

    fun hasAction(behavior: KeyBehavior): Boolean = keyActions[behavior] != null

    fun getAction(behavior: KeyBehavior): KeyAction? = keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } ?: checkKeyAction(sendBindings) ?: click

    private fun checkKeyAction(): KeyAction? {
        val asciiMode = RimeDaemon.isAsciiMode
        val paging = RimeDaemon.isPaging
        val hasMenu = RimeDaemon.hasMenu
        val composing = RimeDaemon.isComposing
        return keyActions[KeyBehavior.ASCII].takeIf { asciiMode }
            ?: keyActions[KeyBehavior.PAGING]?.takeIf { paging }
            ?: keyActions[KeyBehavior.HAS_MENU]?.takeIf { hasMenu }
            ?: keyActions[KeyBehavior.COMPOSING]?.takeIf { composing }
    }

    private fun checkKeyAction(sendBindings: Boolean): KeyAction? = checkKeyAction().takeIf { sendBindings }

    val code: Int
        get() = click?.code ?: KeyEvent.KEYCODE_UNKNOWN

    fun getCode(behavior: KeyBehavior): Int = getAction(behavior)!!.code

    fun getLabel(): String {
        // 1) Double-click on-state labels
        if (isOn && hasAction(KeyBehavior.DOUBLE_CLICK)) {
            return keyActions[KeyBehavior.DOUBLE_CLICK]!!.getLabel(parent)
        }
        if (isOn && hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)) {
            return keyActions[KeyBehavior.LAZY_DOUBLE_CLICK]!!.getLabel(parent)
        }

        // 2) "enter_labels" special label — always shown regardless of mode
        if (checkKeyAction() == null && label.firstOrNull()?.text == "enter_labels") {
            return label.firstOrNull()?.text ?: ""
        }

        // 3) Layout-level label: use ascii_label in ASCII mode, label otherwise
        if (checkKeyAction() == null) {
            val isAscii = RimeDaemon.isAsciiMode
            if (isAscii) {
                val alt = asciiLabel.firstOrNull()?.text?.takeIf { it.isNotEmpty() }
                if (!alt.isNullOrEmpty()) return alt
            } else {
                if (label.any { it.text.isNotEmpty() }) {
                    return label.firstOrNull()?.text ?: ""
                }
            }
        }

        // 4) Fallback to PresetKey label
        return keyAction!!.getLabel(parent)
    }

    fun getPreviewText(behavior: KeyBehavior): String = when (behavior) {
        KeyBehavior.CLICK -> keyAction!!.getPreview(parent)
        else -> getAction(behavior)!!.getPreview(parent)
    }

    val symbolLabel: List<TextKeyboard.LabelSegment>
        get() = labelSymbol.ifEmpty {
            val labelStr = longClick?.getLabel(parent)
                ?: keyActions[KeyBehavior.DOUBLE_CLICK]?.getLabel(parent)
                ?: keyActions[KeyBehavior.LAZY_DOUBLE_CLICK]?.getLabel(parent)
                ?: ""
            if (labelStr.isNotEmpty()) listOf(TextKeyboard.LabelSegment(text = labelStr)) else emptyList()
        }

    private val appearanceType: Int
        get() {
            return when {
                click?.isModifierKey == true && parent.modifier.hasFlag(click!!.modifierKeyOnMask) || isOn -> 2
                click?.isSticky == true || click?.isFunctional == true -> 1
                else -> 0
            }
        }

    fun getBackgroundDrawable(): Drawable? = when (appearanceType) {
        2 -> if (isPressed) hlOnKeyBackground else onKeyBackground
        1 -> {
            if (isPressed) {
                hlOffKeyBackground ?: hlKeyBackground
            } else {
                selfConfig?.keyBackColor.takeIf { !it.isNullOrEmpty() }?.let { keyBackground }
                    ?: (offKeyBackground ?: keyBackground)
            }
        }
        else -> if (isPressed) hlKeyBackground else keyBackground
    }

    fun getTextColor(): Int = when (appearanceType) {
        2 -> if (isPressed) hlOnKeyTextColor else onKeyTextColor
        1 -> if (isPressed) hlOffKeyTextColor else getColor(selfConfig?.keyTextColor ?: "", offKeyTextColor)
        else -> if (isPressed) hlKeyTextColor else keyTextColor
    }

    fun getSymbolColor(): Int = when (appearanceType) {
        2 -> if (isPressed) hlOnKeySymbolColor else onKeySymbolColor
        1 -> if (isPressed) hlOffKeySymbolColor else offKeySymbolColor
        else -> if (isPressed) hlKeySymbolColor else keySymbolColor
    }
}

private const val ICON_PREFIX = "ic@"

val String.isIconFont: Boolean
    get() = startsWith(ICON_PREFIX) || contains("'$ICON_PREFIX")

fun String.toIconName(): String = replace(ICON_PREFIX, "cmd_")

enum class HorizontalAlign { LEFT, CENTER, RIGHT }

sealed class LabelSegment {
    abstract val align: HorizontalAlign
    data class Icon(val cmdName: String, override val align: HorizontalAlign = HorizontalAlign.CENTER) : LabelSegment()
    data class Text(val content: String, override val align: HorizontalAlign = HorizontalAlign.CENTER) : LabelSegment()
}

fun String.parseLabelSegments(): List<LabelSegment> {
    val segments = mutableListOf<LabelSegment>()
    val sb = StringBuilder()
    var i = 0

    while (i < length) {
        if (this[i] == '\'' && i + ICON_PREFIX.length + 1 < length &&
            this.substring(i + 1).startsWith(ICON_PREFIX)
        ) {
            val closeIdx = this.indexOf('\'', i + 1)
            if (closeIdx > i) {
                if (sb.isNotEmpty()) {
                    segments.add(LabelSegment.Text(sb.toString()))
                    sb.clear()
                }
                val iconName = this.substring(i + 1 + ICON_PREFIX.length, closeIdx)
                segments.add(LabelSegment.Icon("cmd_$iconName"))
                i = closeIdx + 1
                continue
            }
        }
        sb.append(this[i])
        i++
    }
    if (sb.isNotEmpty()) {
        val text = sb.toString()
        if (segments.isEmpty() && text.startsWith(ICON_PREFIX)) {
            segments.add(LabelSegment.Icon(text.toIconName()))
        } else {
            segments.add(LabelSegment.Text(text))
        }
    }
    if (segments.isEmpty()) {
        return if (startsWith(ICON_PREFIX)) {
            listOf(LabelSegment.Icon(toIconName()))
        } else {
            listOf(LabelSegment.Text(this))
        }
    }
    return segments
}
