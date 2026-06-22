/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.t9.T9InputController
import com.osfans.trime.ime.t9.T9SidebarView
import com.osfans.trime.ime.voice.WaveformView
import com.osfans.trime.link.AsrkbSpeechClient
import com.osfans.trime.link.SherpaSpeechClient
import com.osfans.trime.link.VoiceOverlayUiBridge
import timber.log.Timber
import androidx.core.graphics.ColorUtils as AndroidColorUtils

// TODO: move layout calculation responsibilities from Keyboard to KeyboardView using ConstraintLayout
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    private val keyboardActionListener: KeyboardActionListener,
    private val enterKeyDisplay: EnterKeyDisplayDelegate,
    private val t9Controller: T9InputController? = null,
) : FrameLayout(context) {

    private val keys get() = keyboard.keys

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.generalStyle.keyTextSize
    internal val keyLongTextSize = theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.generalStyle.symbolTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress

    private var voiceOverlay: FrameLayout? = null
    private var voiceWave: WaveformView? = null

    private var t9Sidebar: T9SidebarView? = null

    init {
        setWillNotDraw(false)
        buildKeyViews()
        if (keyboard.isT9Mode && t9Controller != null) {
            createT9Sidebar()
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun createT9Sidebar() {
        val controller = t9Controller ?: return
        createT9SidebarWithController(controller)
    }

    fun getT9Controller(): T9InputController? = t9Controller

    fun updateT9Controller(controller: T9InputController?) {
        t9Sidebar?.let { removeView(it) }
        t9Sidebar = null
        if (controller != null && keyboard.isT9Mode) {
            createT9SidebarWithController(controller)
        }
    }

    fun repositionT9Sidebar(availableWidth: Int) {
        val sidebar = t9Sidebar ?: return
        val sidebarWidth = (keyboard.minWidth * keyboard.t9SidebarWidth).toInt()
        val isRight = keyboard.t9SidebarPosition == "right"
        val x = if (isRight) {
            (availableWidth - sidebarWidth).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        sidebar.translationX = x
    }

    private fun createT9SidebarWithController(controller: T9InputController) {
        val sidebarWidth = (keyboard.minWidth * keyboard.t9SidebarWidth).toInt()
        val sidebarHeight = keyboard.getT9SidebarHeight()
        val isRight = keyboard.t9SidebarPosition == "right"

        t9Sidebar = T9SidebarView(context, theme, keyboard).apply {
            layoutParams = LayoutParams(sidebarWidth, sidebarHeight)
            val x = if (isRight) {
                (keyboard.minWidth - sidebarWidth).toFloat()
            } else {
                0f
            }
            translationX = x
            translationY = 0f

            onItemSelected = { token ->
                controller.onSelectPinyin(token.pos, token.raw, token.pinYin)
            }
            onSymbolSelected = { symbol ->
                service.commitText(symbol)
            }
        }

        controller.onCandidatesChanged = { tokens ->
            t9Sidebar?.post {
                t9Sidebar?.updateItems(tokens)
            }
        }

        addView(t9Sidebar)
    }

    private fun buildKeyViews() {
        removeAllViews()

        keys.forEachIndexed { index, key ->
            val keyView = createKeyView(index, key)
            addView(keyView)
        }
    }

    private fun createKeyView(index: Int, key: Key): KeyView = KeyView(
        context,
        key = key,
        keyboard = keyboard,
        keyboardView = this,
        keyboardActionListener = keyboardActionListener,
    ).apply {
        id = index

        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        layoutParams = LayoutParams(totalWidth, key.height)

        translationX = (key.x - key.extraWidthLeft).toFloat()
        translationY = key.y.toFloat()

        setPadding(
            (if (key.edgeFlags and Keyboard.EDGE_LEFT == 0) keyboard.horizontalGap / 2 else 0) + key.extraWidthLeft,
            if (key.edgeFlags and Keyboard.EDGE_TOP != 0) keyboard.verticalGap else keyboard.verticalGap / 2,
            (if (key.edgeFlags and Keyboard.EDGE_RIGHT == 0) keyboard.horizontalGap / 2 else 0) + key.extraWidthRight,
            if (key.edgeFlags and Keyboard.EDGE_BOTTOM == 0) keyboard.verticalGap / 2 else 0,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom

        val measuredWidth =
            minOf(MeasureSpec.getSize(widthMeasureSpec), fullWidth)

        measureChildren(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        children.forEach { it.invalidate() }
    }

    fun invalidateKeysByIndices(indices: Set<Int>) {
        for (idx in indices) {
            getChildAt(idx)?.invalidate()
        }
    }

    fun invalidateKeyByIndex(index: Int) {
        getChildAt(index)?.invalidate()
    }

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    fun onDetach() {
        hideVoiceOverlay()
        VoiceOverlayUiBridge.clear()
        if (AsrkbSpeechClient.isHolding()) {
            AsrkbSpeechClient.stopHoldSession()
        } else if (SherpaSpeechClient.isHolding()) {
            SherpaSpeechClient.stopHoldSession()
        }
        popup.dismissAll()
    }

    internal fun showVoiceOverlay() {
        if (voiceOverlay != null) return

        val bgColor = runCatching { ColorManager.getColor("keyboard_back_color") }.getOrElse { Color.BLACK }
        val overlay =
            FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                isClickable = false
                isFocusable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                alpha = 0.75f

                runCatching {
                    ColorManager.getDecorDrawable("keyboard_background")?.also { background = it }
                }.onFailure { Timber.d(it, "Resolve keyboard_background drawable failed") }
                if (background == null) setBackgroundColor(bgColor)
            }

        val candidateColors =
            listOf(
                "key_text_color",
                "hilited_key_text_color",
                "candidate_text_color",
                "hilited_candidate_text_color",
            ).mapNotNull { key ->
                runCatching { ColorManager.getColor(key) }.getOrNull()
            }
        val lineColor =
            candidateColors.firstOrNull { AndroidColorUtils.calculateContrast(it, bgColor) >= 2.5 }
                ?: candidateColors.firstOrNull()
                ?: Color.WHITE

        val wave =
            WaveformView(context, animationStyle = AppPrefs.defaultInstance().voiceInput.voiceAnimationStyle.getValue()).apply {
                setWaveformColor(lineColor)
                visibility = INVISIBLE
            }
        overlay.addView(wave, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val transition =
            TransitionSet().apply {
                addTransition(Slide(Gravity.BOTTOM).apply { addTarget(overlay) })
                duration = 100
            }
        runCatching { TransitionManager.beginDelayedTransition(this, transition) }
            .onFailure { Timber.d(it, "Begin voice overlay transition failed") }

        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        voiceOverlay = overlay
        voiceWave = wave
    }

    internal fun hideVoiceOverlay() {
        val overlay = voiceOverlay ?: return
        runCatching { voiceWave?.stop() }.onFailure { Timber.w(it, "Stop WaveformView failed") }
        val transition =
            TransitionSet().apply {
                addTransition(Slide(Gravity.BOTTOM).apply { addTarget(overlay) })
                duration = 100
            }
        runCatching { TransitionManager.beginDelayedTransition(this, transition) }
            .onFailure { Timber.d(it, "Begin voice overlay transition failed") }
        runCatching { removeView(overlay) }.onFailure { Timber.w(it, "Remove voice overlay failed") }
        voiceOverlay = null
        voiceWave = null
    }

    internal fun startVoiceOverlayWave() {
        val wave = voiceWave ?: return
        wave.visibility = VISIBLE
        wave.start()
    }

    internal fun updateVoiceOverlayAmplitude(amplitude: Float) {
        voiceWave?.updateAmplitude(amplitude)
    }
}
