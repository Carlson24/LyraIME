package com.osfans.trime.ime.dynamic

import android.os.Handler
import android.os.Looper
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import timber.log.Timber

class DynamicController(
    private val rime: RimeSession,
    private val onSwitchRequest: (String) -> Unit,
) {
    private val keyboardList = mutableListOf<String>()
    private var cursorPosition: Int = 0
    var originalKeyboard: String = ".default"

    @Volatile
    var isKeyProcessing: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val messageHandler: (RimeMessage<*>) -> Unit = { message ->
        if (message is RimeMessage.CommitTextMessage) {
            if (!message.data.text.isNullOrEmpty() && keyboardList.isNotEmpty() && !isKeyProcessing) {
                mainHandler.post { clear() }
            }
        }
    }

    val isEmpty: Boolean
        get() = keyboardList.isEmpty()

    init {
        Rime.registerRimeMessageHandler(messageHandler)
    }

    fun onInput(dynamicTarget: String?) {
        val kb = when (dynamicTarget) {
            null -> keyboardAt(cursorPosition)
            ".original" -> originalKeyboard
            else -> dynamicTarget
        }
        Timber.d("dynamic onInput: target=$dynamicTarget, cursor=$cursorPosition, keyboard=$kb, list=$keyboardList")
        keyboardList.add(cursorPosition, kb)
        cursorPosition++
        switchToKeyboardAt(cursorPosition)
    }

    fun onDelete(): Boolean {
        if (cursorPosition <= 0) return false
        keyboardList.removeAt(cursorPosition - 1)
        cursorPosition--
        Timber.d("dynamic onDelete: cursor=$cursorPosition, list=$keyboardList")
        switchToKeyboardAt(cursorPosition)
        return true
    }

    fun onCursorMoved(position: Int) {
        cursorPosition = position.coerceIn(0, keyboardList.size)
        Timber.d("dynamic onCursorMoved: cursor=$cursorPosition")
        switchToKeyboardAt(cursorPosition)
    }

    fun clear() {
        Timber.d("dynamic clear: list was $keyboardList, switching to original=$originalKeyboard")
        keyboardList.clear()
        cursorPosition = 0
        onSwitchRequest(originalKeyboard)
    }

    fun reset() {
        Timber.d("dynamic reset: list was $keyboardList")
        keyboardList.clear()
        cursorPosition = 0
    }

    fun trimCommitted(committed: Int) {
        if (committed <= 0 || keyboardList.isEmpty()) return
        val toRemove = committed.coerceAtMost(keyboardList.size)
        Timber.d("dynamic trimCommitted: removing $toRemove from list=$keyboardList")
        repeat(toRemove) { keyboardList.removeAt(0) }
        cursorPosition = (cursorPosition - toRemove).coerceAtLeast(0)
        onSwitchRequest(originalKeyboard)
    }

    fun destroy() {
        Rime.unregisterRimeMessageHandler(messageHandler)
    }

    private fun switchToKeyboardAt(pos: Int) {
        val kb = if (pos <= 0) originalKeyboard else keyboardList[pos - 1]
        Timber.d("dynamic switchToKeyboardAt: pos=$pos, keyboard=$kb")
        onSwitchRequest(kb)
    }

    private fun keyboardAt(pos: Int): String = if (pos <= 0) originalKeyboard else keyboardList[pos - 1]
}
