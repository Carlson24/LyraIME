package com.osfans.trime.ime.sanpin

import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SanpinController(
    private val rime: RimeSession,
    private val onSwitchRequest: (String) -> Unit,
) {
    private val keyboardStack = ArrayDeque<String>()
    var originalKeyboard: String = ".default"

    private var messageJob: Job? = null

    val isEmpty: Boolean
        get() = keyboardStack.isEmpty()

    init {
        messageJob = rime.lifecycleScope.launch {
            rime.run { messageFlow }.collect { message ->
                if (message is RimeMessage.CommitTextMessage) {
                    val text = message.data.text
                    if (!text.isNullOrEmpty() && keyboardStack.isNotEmpty()) {
                        clear()
                    }
                }
            }
        }
    }

    fun push(target: String) {
        keyboardStack.addLast(target)
        onSwitchRequest(target)
    }

    fun pop(): String? {
        if (keyboardStack.isEmpty()) return null
        keyboardStack.removeLast()
        return keyboardStack.lastOrNull() ?: originalKeyboard
    }

    fun clear() {
        keyboardStack.clear()
        onSwitchRequest(originalKeyboard)
    }

    fun destroy() {
        messageJob?.cancel()
        messageJob = null
    }
}
