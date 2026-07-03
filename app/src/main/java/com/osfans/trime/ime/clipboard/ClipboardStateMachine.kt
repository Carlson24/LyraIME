/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.clipboard

class ClipboardStateMachine(
    private var isEmpty: Boolean = false,
    private var isListening: Boolean = true,
) {
    var onStateChanged: ((State) -> Unit)? = null

    enum class State { Normal, AddMore, EnableListening }

    val currentState: State
        get() = when {
            !isListening -> State.EnableListening
            isEmpty -> State.AddMore
            else -> State.Normal
        }

    fun updateEmpty(empty: Boolean) {
        val prev = currentState
        isEmpty = empty
        if (currentState != prev) onStateChanged?.invoke(currentState)
    }

    fun updateListening(listening: Boolean) {
        val prev = currentState
        isListening = listening
        if (currentState != prev) onStateChanged?.invoke(currentState)
    }
}
