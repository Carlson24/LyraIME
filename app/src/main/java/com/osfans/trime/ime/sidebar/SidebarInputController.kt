package com.osfans.trime.ime.sidebar

import android.view.KeyEvent
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SidebarInputController(
    private val rime: RimeSession,
    private val layout: SidebarLayout,
) {
    data class PinYinToken(
        val pos: Int,
        val raw: String,
        val pinYin: String,
        val display: String = pinYin,
    )

    enum class Behavior {
        NONE,
        NORMAL,
        SEGMENT,
        SELECT_PINYIN,
        SELECT_CANDIDATE,
    }

    private val inputQueue = ArrayDeque<String>()
    private val selectedQueue = ArrayDeque<PinYinToken>()
    private val behaviorQueue = ArrayDeque<Behavior>()

    var onCandidatesChanged: ((List<PinYinToken>) -> Unit)? = null

    private var cachedInputString = ""
    private var lastRimeInput = ""
    private var messageJob: Job? = null

    companion object {
        const val SEGMENT_KEY_CHAR = '\''
        const val SEGMENT_KEY_CHAR_ALIAS = '1'
    }

    init {
        messageJob = rime.lifecycleScope.launch {
            rime.run { messageFlow }.collect { message ->
                if (message is RimeMessage.CommitTextMessage) {
                    val text = message.data.text
                    if (!text.isNullOrEmpty()) {
                        clear()
                    }
                }
            }
        }
    }

    fun destroy() {
        messageJob?.cancel()
        messageJob = null
    }

    fun onKeyChar(char: String) {
        if (char.length != 1) return
        val c = char[0]
        if (c == SEGMENT_KEY_CHAR_ALIAS && layout.isT9Style) {
            onSegmentKey()
            return
        }
        val valid =
            if (layout.isT9Style) {
                layout.isValidKeyChar(c)
            } else {
                layout.isValidKeyChar(c) || !c.isLowerCase()
            }
        if (!valid) return
        inputQueue.add(char)
        cachedInputString += char
        behaviorQueue.add(Behavior.NORMAL)
        fireCandidatesChanged()
    }

    fun onBackspace(): Boolean {
        if (behaviorQueue.isEmpty()) {
            return false
        }
        var modified = false
        when (behaviorQueue.removeLast()) {
            Behavior.SELECT_PINYIN -> {
                if (selectedQueue.isNotEmpty()) {
                    val lastSelected = selectedQueue.last()
                    val code =
                        layout.codeMatchingPhysical(lastSelected.pinYin, lastSelected.raw)
                            ?: SidebarPinYin.keyCodeOf(lastSelected.pinYin, layout)
                            ?: lastSelected.pinYin
                    if (!lastRimeInput.contains(code)) {
                        return false
                    }
                    selectedQueue.removeLast()
                    modified = true
                }
            }

            Behavior.SELECT_CANDIDATE -> {
            }

            else -> {
                if (inputQueue.isNotEmpty()) {
                    inputQueue.removeLast()
                    cachedInputString = cachedInputString.dropLast(1)
                    modified = true
                }
            }
        }
        if (modified) {
            fireCandidatesChanged()
        }
        return modified
    }

    fun onSegmentKey(): Boolean {
        if (inputQueue.isEmpty()) {
            return true
        }
        if (inputQueue.last() == SEGMENT_KEY_CHAR.toString()) {
            return true
        }
        var selectedSize = 0
        selectedQueue.forEach { selectedSize += it.raw.length }
        if (selectedSize == inputQueue.size) {
            return true
        }
        inputQueue.add(SEGMENT_KEY_CHAR.toString())
        cachedInputString += SEGMENT_KEY_CHAR.toString()
        behaviorQueue.add(Behavior.SEGMENT)
        fireCandidatesChanged()
        return false
    }

    fun isSegmentKeyCode(keyEventCode: Int): Boolean = keyEventCode == KeyEvent.KEYCODE_APOSTROPHE

    fun onSelectPinyin(
        pos: Int,
        raw: String,
        pinYin: String,
    ) {
        selectedQueue.add(PinYinToken(pos, raw, pinYin))
        behaviorQueue.add(Behavior.SELECT_PINYIN)
        updateRimeInput()
        fireCandidatesChanged()
    }

    fun computeCandidates(): List<PinYinToken> {
        if (inputQueue.isEmpty()) return emptyList()
        val position = nextSequencePosition()
        if (position < 0) return emptyList()
        val sequence = cachedInputString.substring(position)
        return SidebarPinYin.possibleCombinations(sequence, layout).mapNotNull { pinYin ->
            val code = layout.physicalCodeOf(pinYin) ?: return@mapNotNull null
            var raw = sequence.substring(0, minOf(code.length, sequence.length))
            if (sequence.getOrNull(code.length) == SEGMENT_KEY_CHAR) {
                raw += SEGMENT_KEY_CHAR.toString()
            }
            PinYinToken(position, raw, pinYin, display = SidebarPinYin.displayPinyin(pinYin))
        }
    }

    fun buildRimeInput(): String {
        val input = cachedInputString
        if (selectedQueue.isEmpty()) return input
        val first = selectedQueue.first()
        val last = selectedQueue.last()
        val start = first.pos
        val end = last.pos + last.raw.length
        if (start < 0 || end > input.length) return input
        val result = StringBuilder().append(input.substring(0, start))
        var cursor = start
        val rawTail = input.substring(end)
        var trailingSeparator = false
        for ((index, token) in selectedQueue.withIndex()) {
            if (token.pos > cursor) {
                result.append(input.substring(cursor, token.pos))
            }
            val rawEnd = token.pos + token.raw.length
            if (rawEnd <= input.length && input.regionMatches(
                    token.pos,
                    token.raw,
                    0,
                    token.raw.length,
                )
            ) {
                result.append(
                    layout.codeMatchingPhysical(token.pinYin, token.raw)
                        ?: SidebarPinYin.keyCodeOf(token.pinYin, layout)
                        ?: token.pinYin,
                )
                val separatorNeeded =
                    layout.scheme == PinyinScheme.FULL || token.raw.endsWith(SEGMENT_KEY_CHAR.toString())
                if (index == selectedQueue.lastIndex && rawTail.isNotEmpty() && rawTail.all { !it.isLowerCase() }) {
                    trailingSeparator = separatorNeeded
                } else if (separatorNeeded) {
                    result.append(SEGMENT_KEY_CHAR)
                }
            } else {
                result.append(input.substring(token.pos, rawEnd))
            }
            cursor = rawEnd
        }
        result.append(input.substring(end))
        if (trailingSeparator) {
            result.append(SEGMENT_KEY_CHAR)
        }
        return result.toString()
    }

    fun updateRimeInput() {
        val input = buildRimeInput()
        lastRimeInput = input
        rime.lifecycleScope.launch {
            rime.runOnReady {
                setRawInput(input)
            }
        }
    }

    fun clear() {
        inputQueue.clear()
        selectedQueue.clear()
        behaviorQueue.clear()
        cachedInputString = ""
        fireCandidatesChanged()
    }

    private fun fireCandidatesChanged() {
        onCandidatesChanged?.invoke(computeCandidates())
    }

    private fun nextSequencePosition(): Int {
        if (selectedQueue.isEmpty()) return 0
        var pos = 0
        for (token in selectedQueue) {
            if (token.pos > pos) return pos
            val end = token.pos + token.raw.length
            if (end > pos) pos = end
        }
        if (pos >= inputQueue.size) return pos
        return pos
    }
}
