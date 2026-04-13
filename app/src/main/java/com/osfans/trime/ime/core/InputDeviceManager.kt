/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.CandidatesView
import com.osfans.trime.util.isLandscape
import com.osfans.trime.util.monitorCursorAnchor

class InputDeviceManager(
    private val onChange: (Boolean) -> Unit,
) {
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    private val candidatesViewMode by AppPrefs.defaultInstance().candidates.mode
    private val disableWindowOnLandscape by AppPrefs.defaultInstance().candidates.disableWindowOnLandscape

    val effectiveWindowMode: PopupCandidatesMode
        get() = if (disableWindowOnLandscape && candidatesView?.context?.resources?.configuration?.isLandscape() == true) PopupCandidatesMode.DISABLED else candidatesViewMode

    private val alwaysShowCandidatesView: Boolean
        get() = effectiveWindowMode == PopupCandidatesMode.ALWAYS_SHOW

    private fun setupInputViewCallback(isVirtual: Boolean) {
        inputView?.handleMessages = isVirtual
        inputView?.visibility = if (isVirtual) View.VISIBLE else View.GONE
    }

    private fun setupCandidatesViewCallback(isVirtual: Boolean) {
        val shouldSetupView = !isVirtual || alwaysShowCandidatesView
        candidatesView?.handleMessages = shouldSetupView
        if (!shouldSetupView) {
            candidatesView?.visibility = View.GONE
        }
    }

    private fun setupViewCallbacks(isVirtual: Boolean) {
        setupInputViewCallback(isVirtual)
        setupCandidatesViewCallback(isVirtual)
    }

    var isVirtualKeyboard = true
        private set(value) {
            field = value
            setupViewCallbacks(value)
        }

    fun setInputView(inputView: InputView) {
        this.inputView = inputView
        setupInputViewCallback(this.isVirtualKeyboard)
    }

    fun setCandidatesView(candidatesView: CandidatesView) {
        this.candidatesView = candidatesView
        currentAlwaysShowCandidatesView = alwaysShowCandidatesView
        setupCandidatesViewCallback(this.isVirtualKeyboard)
    }

    private var currentAlwaysShowCandidatesView: Boolean = false

    private fun applyMode(
        service: TrimeInputMethodService,
        useVirtualKeyboard: Boolean,
    ) {
        val useCandidatesView = !useVirtualKeyboard || alwaysShowCandidatesView
        service.postRimeJob {
            // restart rime or start rime deploy will reset the options
            // in rime engine, so we need to always set the option on
            // each evaluation
            setRuntimeOption("paging_mode", useCandidatesView)
        }
        val alwaysShowChanged = alwaysShowCandidatesView != currentAlwaysShowCandidatesView
        if (useVirtualKeyboard == isVirtualKeyboard && !alwaysShowChanged) {
            return
        }
        currentAlwaysShowCandidatesView = alwaysShowCandidatesView
        if (alwaysShowChanged && useVirtualKeyboard == isVirtualKeyboard) {
            setupCandidatesViewCallback(isVirtualKeyboard)
            return
        }
        // monitor CursorAnchorInfo when switching to CandidatesView
        service.currentInputConnection.monitorCursorAnchor(!useVirtualKeyboard)
        isVirtualKeyboard = useVirtualKeyboard
        onChange(isVirtualKeyboard)
    }

    fun reapplyWindowMode(service: TrimeInputMethodService) {
        val prevAlwaysShow = currentAlwaysShowCandidatesView
        applyMode(service, isVirtualKeyboard)
        if (currentAlwaysShowCandidatesView != prevAlwaysShow) {
            service.postRimeJob { clearComposition() }
        }
    }

    private var startedInputView = false
    private var isNullInputType = true

    /**
     * @return should use virtual keyboard or should use candidates view
     */
    fun evaluateOnStartInputView(
        info: EditorInfo,
        service: TrimeInputMethodService,
    ): Pair<Boolean, Boolean> {
        startedInputView = true
        isNullInputType = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        val useVirtualKeyboard =
            when (effectiveWindowMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE -> isVirtualKeyboard
                PopupCandidatesMode.ALWAYS_SHOW -> true
                PopupCandidatesMode.DISABLED -> true
            }
        val useCandidatesView = !useVirtualKeyboard || alwaysShowCandidatesView
        applyMode(service, useVirtualKeyboard)
        return useVirtualKeyboard to useCandidatesView
    }

    /**
     * @return should force show input views
     */
    fun evaluateOnKeyDown(
        e: KeyEvent,
        service: TrimeInputMethodService,
    ): Boolean {
        if (startedInputView) {
            // filter out back/home/volume buttons and combination keys
            if (e.isPrintingKey && e.hasNoModifiers()) {
                // evaluate virtual keyboard visibility when pressing physical keyboard while InputView visible
                evaluateOnKeyDownInner(service)
            }
            // no need to force show InputView since it's already visible
            return false
        } else {
            // force show InputView when focusing on text input (likely inputType is not TYPE_NULL)
            // and pressing any digit/letter/punctuation key on physical keyboard
            val showInputView = !isNullInputType && e.isPrintingKey && e.hasNoModifiers()
            if (showInputView) {
                evaluateOnKeyDownInner(service)
            }
            return showInputView
        }
    }

    private fun evaluateOnKeyDownInner(service: TrimeInputMethodService) {
        val useVirtualKeyboard =
            when (effectiveWindowMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE -> false
                PopupCandidatesMode.ALWAYS_SHOW -> false
                PopupCandidatesMode.DISABLED -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun evaluateOnViewClicked(service: TrimeInputMethodService) {
        if (!startedInputView) return
        val useVirtualKeyboard =
            when (effectiveWindowMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                else -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun evaluateOnUpdateEditorToolType(
        toolType: Int,
        service: TrimeInputMethodService,
    ) {
        if (!startedInputView) return
        val useVirtualKeyboard =
            when (effectiveWindowMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE ->
                    // switch to virtual keyboard on touch screen events, otherwise preserve current mode
                    if (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                        true
                    } else {
                        isVirtualKeyboard
                    }
                PopupCandidatesMode.ALWAYS_SHOW -> true
                PopupCandidatesMode.DISABLED -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun onFinishInputView() {
        startedInputView = false
    }
}
