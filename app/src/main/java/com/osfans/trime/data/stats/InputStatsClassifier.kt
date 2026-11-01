/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

/**
 * Counts Han characters and classifies a committed text by its Han character count.
 *
 * Buckets: 1 = single character, 2 = two characters, 3 = three characters,
 * 4 = four characters, 5 = more than four characters.
 */
object InputStatsClassifier {
    const val MAX_BUCKET = 5

    data class Sample(
        val bucket: Int,
        val hanCount: Int,
    )

    fun classify(text: String): Sample? {
        val hanCount = countHan(text)
        if (hanCount <= 0) return null
        return Sample(
            bucket = hanCount.coerceAtMost(MAX_BUCKET),
            hanCount = hanCount,
        )
    }

    fun countHan(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                count++
            }
            index += Character.charCount(codePoint)
        }
        return count
    }
}
