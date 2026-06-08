/*
 * SPDX-FileCopyrightText: 2026 BryceWG
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import androidx.core.content.ContextCompat
import com.osfans.trime.ime.core.TrimeInputMethodService

/**
 * Shared controller for the ASRKB hold-to-talk session and overlay callbacks.
 * It keeps the UI bridge wiring out of keyboard/tool-bar entry points so future merges stay smaller.
 */
internal class AsrkbVoiceHoldSessionController(
    private val service: TrimeInputMethodService,
    private val showOverlay: () -> Unit,
    private val startWave: () -> Unit,
    private val updateAmplitude: (Float) -> Unit,
    private val hideOverlay: () -> Unit,
    private val onSessionFinished: (() -> Unit)? = null,
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

        AsrkbSpeechClient.startHoldSession(service)
    }

    fun stopIfStarted() {
        if (!startedByController) return
        stop()
    }

    fun stop() {
        startedByController = false
        hideOverlay()
        VoiceOverlayUiBridge.clear()
        onSessionFinished?.invoke()
        if (AsrkbSpeechClient.isHolding()) {
            AsrkbSpeechClient.stopHoldSession()
        }
    }
}
