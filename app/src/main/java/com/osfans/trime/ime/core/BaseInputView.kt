/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.View
import android.view.WindowInsets
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.ColorManager
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

    private var candidateActionMenu: PopupMenu? = null

    fun showCandidateActionMenu(idx: Int, text: String, view: View, global: Boolean) {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        val highlightColor = ColorManager.getColor("hilited_candidate_text_color")
        val title = buildSpannedString {
            bold {
                color(highlightColor) { append(text) }
            }
        }
        val popupActions = theme.candidatesTool?.popup
        service.lifecycleScope.launch {
            InputFeedbackManager.keyPressVibrate(view, longPress = true)
            candidateActionMenu =
                PopupMenu(themedContext, view).apply {
                    menu.add(title).apply {
                        isEnabled = false
                    }
                    if (popupActions.isNullOrEmpty()) {
                        menu.add(R.string.forget_this_word).setOnMenuItemClickListener {
                            rime.runIfReady { deleteCandidate(idx, global) }
                            true
                        }
                    } else {
                        popupActions.forEach { popupAction ->
                            val label = popupAction.label.ifEmpty {
                                KeyboardWindow.currentKeyboard.let { kb ->
                                    KeyActionManager.getAction(popupAction.action).getLabel(kb)
                                }
                            }
                            menu.add(label).setOnMenuItemClickListener {
                                handlePopupAction(popupAction.action, idx, global)
                                true
                            }
                        }
                    }
                    setOnDismissListener {
                        candidateActionMenu = null
                    }
                    show()
                }
        }
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
        rime.launchOnReady { rimeApi ->
            rimeApi.processKey(rimeKeyVal, keyAction.modifier.toUInt())
        }
    }

    private val navBarBackground by ThemeManager.prefs.navbarBackground

    private val navBarFrameHeight: Int
        get() {
            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
            return try {
                resources.getDimensionPixelSize(resId)
            } catch (e: Resources.NotFoundException) {
                dp(FALLBACK_NAVBAR_HEIGHT)
            }
        }

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navBarBackground != ThemePrefs.NavbarBackground.FULL) {
            return 0
        }

        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)

        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
        val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
        val systemGestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
        val threshold = (resources.displayMetrics.density * 40).toInt()

        return when {
            nav > 0 -> nav
            tappable > 0 -> tappable
            mandatory > threshold -> mandatory
            systemGestures > threshold -> systemGestures
            else -> 0
        }
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
