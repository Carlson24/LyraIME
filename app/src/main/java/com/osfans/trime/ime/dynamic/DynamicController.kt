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
    private val charCounts = mutableListOf<Int>()
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
        charCounts.add(cursorPosition, 1)
        cursorPosition++
        switchToKeyboardAt(cursorPosition)
    }

    fun recordCharCount(count: Int) {
        if (cursorPosition > 0 && charCounts.size >= cursorPosition) {
            charCounts[cursorPosition - 1] = count.coerceAtLeast(1)
        }
    }

    fun keyIndexFromCaret(caretPos: Int): Int {
        if (caretPos <= 0) return 0
        var remaining = caretPos
        for (i in charCounts.indices) {
            remaining -= charCounts[i]
            if (remaining <= 0) return i + 1
        }
        return charCounts.size
    }

    fun onDelete(count: Int = 1): Boolean {
        if (count <= 0 || cursorPosition <= 0) return false
        val actualCount = count.coerceAtMost(cursorPosition)
        cursorPosition -= actualCount
        repeat(actualCount) {
            keyboardList.removeAt(cursorPosition)
            charCounts.removeAt(cursorPosition)
        }
        Timber.d("dynamic onDelete: removed=$actualCount, cursor=$cursorPosition, list=$keyboardList")
        switchToKeyboardAt(cursorPosition)
        return true
    }

    fun onCursorMoved(position: Int) {
        if (isKeyProcessing) return
        val newPos = position.coerceIn(0, keyboardList.size)
        if (newPos == cursorPosition) return
        cursorPosition = newPos
        Timber.d("dynamic onCursorMoved: cursor=$cursorPosition")
        switchToKeyboardAt(cursorPosition)
    }

    fun clear() {
        Timber.d("dynamic clear: list was $keyboardList, switching to original=$originalKeyboard")
        keyboardList.clear()
        charCounts.clear()
        cursorPosition = 0
        onSwitchRequest(originalKeyboard)
    }

    fun reset() {
        Timber.d("dynamic reset: list was $keyboardList")
        keyboardList.clear()
        charCounts.clear()
        cursorPosition = 0
    }

    fun trimCommitted(committed: Int) {
        if (committed <= 0 || keyboardList.isEmpty()) return
        val toRemove = committed.coerceAtMost(keyboardList.size)
        Timber.d("dynamic trimCommitted: removing $toRemove from list=$keyboardList")
        repeat(toRemove) {
            keyboardList.removeAt(0)
            charCounts.removeAt(0)
        }
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
