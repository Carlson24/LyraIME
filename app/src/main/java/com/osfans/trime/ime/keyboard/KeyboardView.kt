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
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.voice.WaveformView
import com.osfans.trime.link.AsrkbSpeechClient
import com.osfans.trime.link.SherpaSpeechClient
import com.osfans.trime.link.VoiceOverlayUiBridge
import timber.log.Timber
import androidx.core.graphics.ColorUtils as AndroidColorUtils

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    private val keyboardActionListener: KeyboardActionListener,
    private val enterKeyDisplay: EnterKeyDisplayDelegate,
) : LinearLayout(context) {

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.generalStyle.keyTextSize
    internal val keyLongTextSize = theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.generalStyle.symbolTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress

    private var voiceOverlay: FrameLayout? = null
    private var voiceWave: WaveformView? = null

    private val keyViews = mutableListOf<KeyView>()

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        buildKeyViews()
    }

    private fun buildKeyViews() {
        removeAllViews()
        keyViews.clear()

        for (rowLayout in keyboard.rowLayouts) {
            val flexRow = FlexboxLayout(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.NOWRAP
                alignItems = AlignItems.STRETCH
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowLayout.pixelHeight)
            }

            for (entry in rowLayout.entries) {
                when (entry) {
                    is Keyboard.RowLayoutEntry.KeyRef -> {
                        val keyView = createKeyView(entry.key, entry.weight)
                        flexRow.addView(keyView)
                    }
                    is Keyboard.RowLayoutEntry.Spacer -> {
                        val space = Space(context)
                        space.layoutParams = FlexboxLayout.LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
                            flexGrow = entry.weight
                        }
                        flexRow.addView(space)
                    }
                }
            }

            addView(flexRow)
        }
    }

    private fun createKeyView(key: Key, weight: Float): KeyView = KeyView(
        context,
        key = key,
        keyboard = keyboard,
        keyboardView = this,
        keyboardActionListener = keyboardActionListener,
    ).apply {
        id = key.index

        layoutParams = FlexboxLayout.LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
            flexGrow = weight
        }

        setPadding(
            keyboard.horizontalGap / 2,
            keyboard.verticalGap / 2,
            keyboard.horizontalGap / 2,
            keyboard.verticalGap / 2,
        )

        keyViews.add(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom

        val measuredWidth =
            minOf(
                MeasureSpec.getSize(widthMeasureSpec),
                fullWidth,
            )

        measureChildren(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        keyViews.forEach { it.invalidate() }
    }

    fun invalidateKeyByIndex(index: Int) {
        keyViews.getOrNull(index)?.invalidate()
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
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
                visibility = View.INVISIBLE
            }
        overlay.addView(wave, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

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
        wave.visibility = View.VISIBLE
        wave.start()
    }

    internal fun updateVoiceOverlayAmplitude(amplitude: Float) {
        voiceWave?.updateAmplitude(amplitude)
    }
}
