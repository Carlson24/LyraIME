/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.toast

/**
 * Shared controller for the hold-to-talk session and overlay callbacks.
 * When [useAidl] is true, delegates to [AsrkbSpeechClient] (external AIDL service).
 * When [useAidl] is false and the preferred voice input is built-in, delegates to
 * [SherpaSpeechClient] (local offline ASR). When [useAidl] is false and the preferred
 * voice input is a third-party IME, switches to that IME's voice subtype instead.
 */
internal class AsrkbVoiceHoldSessionController(
    private val service: TrimeInputMethodService,
    private val showOverlay: () -> Unit,
    private val startWave: () -> Unit,
    private val updateAmplitude: (Float) -> Unit,
    private val hideOverlay: () -> Unit,
    private val onSessionFinished: (() -> Unit)? = null,
    private val useAidl: () -> Boolean = { true },
) {
    private var startedByController = false

    fun isRunning(): Boolean = startedByController

    fun start() {
        if (startedByController) return

        if (useAidl()) {
            startLocalOrAidlSession(AsrkbSpeechClient::startHoldSession)
            return
        }

        if (delegateToThirdPartyVoice()) return

        startLocalOrAidlSession(SherpaSpeechClient::startHoldSession)
    }

    /** Delegate dictation to the selected third-party IME's voice subtype. Returns true when handled. */
    private fun delegateToThirdPartyVoice(): Boolean {
        val pref = AppPrefs.defaultInstance().voiceInput.preferredVoiceInput.getValue()
        if (pref.isEmpty() || pref == InputMethodUtils.BUILTIN_VOICE_INPUT) return false

        val target = InputMethodUtils.voiceInputMethods()
            .find { it.first.packageName == pref }
        if (target == null) {
            service.toast(R.string.no_voice_input_installed)
            return true
        }
        InputMethodUtils.switchInputMethod(service, target.first.id, target.second)
        return true
    }

    private fun startLocalOrAidlSession(start: (TrimeInputMethodService) -> Unit) {
        val executor = ContextCompat.getMainExecutor(service)
        showOverlay()
        startedByController = true

        VoiceOverlayUiBridge.onRecordingStarted = {
            executor.execute {
                startWave()
            }
        }
        VoiceOverlayUiBridge.onAmplitude = { amplitude ->
            executor.execute {
                updateAmplitude(amplitude)
            }
        }
        VoiceOverlayUiBridge.onDone = {
            startedByController = false
            executor.execute {
                hideOverlay()
                onSessionFinished?.invoke()
            }
            VoiceOverlayUiBridge.clear()
        }

        start(service)
    }

    fun stopIfStarted() {
        if (!startedByController) return
        stop()
    }

    fun stop() {
        val wasStarted = startedByController
        startedByController = false
        hideOverlay()
        VoiceOverlayUiBridge.clear()
        if (wasStarted) {
            onSessionFinished?.invoke()
        }
        if (useAidl()) {
            if (AsrkbSpeechClient.isHolding()) {
                AsrkbSpeechClient.stopHoldSession()
            }
        } else {
            if (SherpaSpeechClient.isHolding()) {
                SherpaSpeechClient.stopHoldSession()
            }
        }
    }
}
