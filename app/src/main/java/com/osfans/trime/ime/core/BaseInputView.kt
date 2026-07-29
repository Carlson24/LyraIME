/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.core.withTheme
import kotlin.math.max
import androidx.core.graphics.drawable.toDrawable

abstract class BaseInputView(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
) : ConstraintLayout(service) {
    protected abstract fun handleRimeMessage(it: RimeMessage<*>)

    private var messageHandlerJob: Job? = null

    private fun setupRimeMessageHandler() {
        messageHandlerJob =
            service.lifecycleScope.launch {
                rime.run { messageFlow }.collect {
                    handleRimeMessage(it)
                }
            }
    }

    var handleMessages = false
        set(value) {
            field = value
            if (field) {
                if (messageHandlerJob == null) {
                    setupRimeMessageHandler()
                }
            } else {
                messageHandlerJob?.cancel()
                messageHandlerJob = null
            }
        }

    val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private var candidateActionMenu: ListPopupWindow? = null

    fun showCandidateActionMenu(idx: Int, text: String, view: View, global: Boolean, type: String = "") {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        val tool = theme.candidatesTool
        val typeConfig = tool?.popupByType?.get(type)
        val popupWidthDp = typeConfig?.popupWidth?.takeIf { it > 0 } ?: (tool?.popupWidth ?: 0)
        val highlightColor = ColorManager.getColor("hilited_candidate_text_color")
        val density = resources.displayMetrics.density
        val textPaint =
            TextPaint().apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, themedContext.resources.displayMetrics)
            }
        val availWidthPx = (popupWidthDp - 24) * density
        val title = buildSpannedString {
            bold {
                color(highlightColor) {
                    append(truncateText(text, popupWidthDp, textPaint, availWidthPx))
                }
            }
        }
        val popupActions = typeConfig?.popup ?: tool?.popup
        val popupTextSize = tool?.popupTextSize ?: 0f
        val popupTextColorKey = tool?.popupTextColor?.takeIf { it.isNotEmpty() } ?: "candidate_text_color"
        val popupTextColor = ColorManager.getColor(popupTextColorKey)
        val popupBackgroundColorKey = tool?.popupBackgroundColor?.takeIf { it.isNotEmpty() } ?: "candidate_background"
        val popupBackgroundColor = ColorManager.getColor(popupBackgroundColorKey)
        val popupTypeface = FontManager.getTypeface("candidates_tool_popup_font")
            .takeIf { it != Typeface.DEFAULT }
        service.lifecycleScope.launch {
            InputFeedbackManager.keyPressVibrate(view, longPress = true)
            val items = mutableListOf<CharSequence>()
            items.add(title)
            if (popupActions.isNullOrEmpty()) {
                items.add(themedContext.getString(R.string.forget_this_word))
            } else {
                popupActions.forEach { popupAction ->
                    val rawLabel = popupAction.label.ifEmpty {
                        KeyboardWindow.currentKeyboard.let { kb ->
                            KeyActionManager.getAction(popupAction.action).getLabel(kb)
                        }
                    }
                    items.add(truncateText(rawLabel, popupWidthDp, textPaint, availWidthPx))
                }
            }
            candidateActionMenu = ListPopupWindow(themedContext).apply {
                anchorView = view
                if (popupBackgroundColor != 0) {
                    setBackgroundDrawable(popupBackgroundColor.toDrawable())
                }
                if (popupWidthDp > 0) setContentWidth(dp(popupWidthDp))
                setAdapter(object : ArrayAdapter<CharSequence>(
                    themedContext,
                    R.layout.candidate_popup_item,
                    items,
                ) {
                    override fun isEnabled(position: Int) = position != 0
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val hPad = dp(8)
                        val vPad = if (position == 0) dp(8) else dp(6)
                        view.setPadding(hPad, vPad, hPad, vPad)
                        if (popupTextSize > 0f) {
                            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, popupTextSize)
                        }
                        view.setTextColor(popupTextColor)
                        if (popupTypeface != null) {
                            view.typeface = popupTypeface
                        }
                        return view
                    }
                })
                setOnItemClickListener { _, _, position, _ ->
                    if (popupActions.isNullOrEmpty()) {
                        rime.runIfReady { deleteCandidate(idx, global) }
                    } else {
                        handlePopupAction(popupActions[position - 1].action, idx, global)
                    }
                }
                setOnDismissListener {
                    candidateActionMenu = null
                }
                show()
            }
        }
    }

    private fun truncateText(
        text: String,
        maxWidthDp: Int,
        paint: TextPaint,
        availWidthPx: Float,
    ): CharSequence {
        if (maxWidthDp <= 0 || availWidthPx <= 0) return text
        return TextUtils.ellipsize(text, paint, availWidthPx, TextUtils.TruncateAt.END)
    }

    private fun handlePopupAction(action: String, idx: Int, global: Boolean) {
        if (action == "DeleteCandidate") {
            rime.runIfReady { deleteCandidate(idx, global) }
            return
        }
        val keyAction = KeyActionManager.getAction(action)
        val rimeKeyVal = RimeKeyMapping
            .keyCodeToVal(keyAction.code)
            .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
            ?: RimeKeyEvent.getKeycodeByName(Keycode.keyNameOf(keyAction.code))
        val rimeMods = KeyModifiers.fromMetaState(keyAction.modifier)
        rime.launchOnReady { rimeApi ->
            rimeApi.processKey(rimeKeyVal, rimeMods.modifiers)
        }
    }

    private val navBarBackground by ThemeManager.prefs.navbarBackground

    private val navBarFrameHeight: Int
        get() {
            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
            return try {
                resources.getDimensionPixelSize(resId)
            } catch (_: Resources.NotFoundException) {
                dp(FALLBACK_NAVBAR_HEIGHT)
            }
        }

    private val ignoreSystemGestureInsets by AppPrefs.defaultInstance().advanced.ignoreSystemGestureInsets

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navBarBackground != ThemePrefs.NavbarBackground.FULL) {
            return 0
        }
        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        var mask = WindowInsetsCompat.Type.navigationBars()
        if (!ignoreSystemGestureInsets) {
            mask = mask or WindowInsetsCompat.Type.mandatorySystemGestures() or
                WindowInsetsCompat.Type.systemGestures()
        }
        val insetsBottom = insets.getInsets(mask).bottom
        return if (insetsBottom > 0) max(insetsBottom, navBarFrameHeight) else insetsBottom
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // on API 35+, we must call requestApplyInsets() manually after replacing views,
        // otherwise View#onApplyWindowInsets won't be called. ¯\_(ツ)_/¯
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        handleMessages = false
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FALLBACK_NAVBAR_HEIGHT = 48
    }
}
