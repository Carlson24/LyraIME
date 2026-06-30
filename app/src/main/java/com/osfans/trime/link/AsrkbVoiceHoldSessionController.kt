/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import androidx.core.content.ContextCompat
import com.osfans.trime.ime.core.TrimeInputMethodService

/**
 * Shared controller for the hold-to-talk session and overlay callbacks.
 * When [useAidl] is true, delegates to [AsrkbSpeechClient] (external AIDL service).
 * When [useAidl] is false, delegates to [SherpaSpeechClient] (local offline ASR).
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

        if (useAidl()) {
            AsrkbSpeechClient.startHoldSession(service)
        } else {
            SherpaSpeechClient.startHoldSession(service)
        }
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
