/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

/**
 * A snapshot of the text in the target editor.
 *
 * [selectionStart] and [selectionEnd] are relative to [text], as reported by
 * [android.view.inputmethod.ExtractedText].
 */
data class TargetTextSnapshot(
    val text: String,
    val startOffset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val editable: Boolean,
    val truncatedBefore: Boolean = false,
    val truncatedAfter: Boolean = false,
)

/**
 * The text and offsets that should be rendered in the floating sync box.
 *
 * [windowStart] is the index in [text] where the visible part of the document
 * starts. It is non-zero only when a leading ellipsis is present.
 */
data class SyncInputDisplay(
    val text: String,
    val documentStartOffset: Int,
    val windowStart: Int,
    val windowLength: Int,
    val cursor: Int?,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val editable: Boolean,
    val truncatedBefore: Boolean,
    val truncatedAfter: Boolean,
) {
    fun displayOffsetToDocument(offset: Int): Int? {
        val relative = offset - windowStart
        return when {
            relative < 0 -> if (truncatedBefore) documentStartOffset else null
            relative > windowLength -> if (truncatedAfter) documentStartOffset + windowLength else null
            else -> documentStartOffset + relative
        }
    }

    fun documentOffsetToDisplay(offset: Int): Int? {
        val relative = offset - documentStartOffset
        return if (relative in 0..windowLength) windowStart + relative else null
    }
}

object SyncInputTextFormatter {
    private const val MAX_TEXT_LENGTH = 20_000
    private const val WINDOW_BEFORE = 5_000
    private const val WINDOW_AFTER = 5_000
    private const val ELLIPSIS = "…"

    fun format(snapshot: TargetTextSnapshot): SyncInputDisplay {
        val source = snapshot.text
        val sourceLength = source.length
        val selectionStart = snapshot.selectionStart.coerceIn(0, sourceLength)
        val selectionEnd = snapshot.selectionEnd.coerceIn(0, sourceLength)

        var windowStart = 0
        var windowEnd = sourceLength
        if (sourceLength > MAX_TEXT_LENGTH) {
            val cursor = selectionEnd
            windowStart = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
            windowEnd = (cursor + WINDOW_AFTER).coerceAtMost(sourceLength)
        }

        val truncatedBefore = windowStart > 0 || snapshot.truncatedBefore
        val truncatedAfter = windowEnd < sourceLength || snapshot.truncatedAfter

        val prefix = if (truncatedBefore) ELLIPSIS else ""
        val suffix = if (truncatedAfter) ELLIPSIS else ""
        val window = source.substring(windowStart, windowEnd)
        val displayText = prefix + window + suffix
        val displayWindowStart = prefix.length
        val windowLength = window.length
        val documentStartOffset = snapshot.startOffset + windowStart

        val cursor =
            if (selectionEnd in windowStart..windowEnd) {
                displayWindowStart + (selectionEnd - windowStart)
            } else {
                null
            }

        val selectionVisible = selectionStart <= windowEnd && selectionEnd >= windowStart
        val displaySelectionStart =
            if (selectionVisible) {
                displayWindowStart + (selectionStart.coerceIn(windowStart, windowEnd) - windowStart)
            } else {
                null
            }
        val displaySelectionEnd =
            if (selectionVisible) {
                displayWindowStart + (selectionEnd.coerceIn(windowStart, windowEnd) - windowStart)
            } else {
                null
            }

        return SyncInputDisplay(
            text = displayText,
            documentStartOffset = documentStartOffset,
            windowStart = displayWindowStart,
            windowLength = windowLength,
            cursor = cursor,
            selectionStart = displaySelectionStart,
            selectionEnd = displaySelectionEnd,
            editable = snapshot.editable,
            truncatedBefore = truncatedBefore,
            truncatedAfter = truncatedAfter,
        )
    }
}
