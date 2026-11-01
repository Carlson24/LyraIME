/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

data class InputStatsTotals(
    val entries: Long = 0,
    val chars: Long = 0,
) {
    operator fun plus(other: InputStatsTotals) = InputStatsTotals(
        entries = entries + other.entries,
        chars = chars + other.chars,
    )
}

data class InputStatsSnapshot(
    val keyboard: InputStatsTotals = InputStatsTotals(),
    val keyboardToday: InputStatsTotals = InputStatsTotals(),
    val buckets: Map<Int, InputStatsTotals> = emptyMap(),
    val voiceBuiltin: InputStatsTotals = InputStatsTotals(),
    val voiceBuiltinToday: InputStatsTotals = InputStatsTotals(),
    val voiceAidl: InputStatsTotals = InputStatsTotals(),
    val voiceAidlToday: InputStatsTotals = InputStatsTotals(),
    val schemas: List<InputStatsSchemaEntity> = emptyList(),
    val activeDays: Int = 0,
    val keyboardActiveMs: Long = 0,
    val keyboardTimedChars: Long = 0,
) {
    val keyboardSpeedCharsPerMinute: Double?
        get() = if (keyboardActiveMs > 0L) {
            keyboardTimedChars * 60_000.0 / keyboardActiveMs
        } else {
            null
        }

    companion object {
        fun build(
            daily: List<InputStatsDailyEntity>,
            schemas: List<InputStatsSchemaEntity>,
            today: String,
        ): InputStatsSnapshot {
            var keyboard = InputStatsTotals()
            var keyboardToday = InputStatsTotals()
            var voiceBuiltin = InputStatsTotals()
            var voiceBuiltinToday = InputStatsTotals()
            var voiceAidl = InputStatsTotals()
            var voiceAidlToday = InputStatsTotals()
            var keyboardActiveMs = 0L
            var keyboardTimedChars = 0L
            val buckets = mutableMapOf<Int, InputStatsTotals>()
            val activeDays = mutableSetOf<String>()

            daily.forEach { row ->
                val totals = InputStatsTotals(row.entryCount, row.charCount)
                activeDays += row.day
                when (row.origin) {
                    InputStats.ORIGIN_RIME -> {
                        keyboard += totals
                        keyboardActiveMs += row.activeMs
                        keyboardTimedChars += row.timedChars
                        if (row.day == today) {
                            keyboardToday += totals
                        }
                        buckets[row.bucket] = (buckets[row.bucket] ?: InputStatsTotals()) + totals
                    }

                    InputStats.ORIGIN_VOICE_BUILTIN -> {
                        voiceBuiltin += totals
                        if (row.day == today) {
                            voiceBuiltinToday += totals
                        }
                    }

                    InputStats.ORIGIN_VOICE_AIDL -> {
                        voiceAidl += totals
                        if (row.day == today) {
                            voiceAidlToday += totals
                        }
                    }
                }
            }

            return InputStatsSnapshot(
                keyboard = keyboard,
                keyboardToday = keyboardToday,
                buckets = buckets,
                voiceBuiltin = voiceBuiltin,
                voiceBuiltinToday = voiceBuiltinToday,
                voiceAidl = voiceAidl,
                voiceAidlToday = voiceAidlToday,
                schemas = schemas,
                activeDays = activeDays.size,
                keyboardActiveMs = keyboardActiveMs,
                keyboardTimedChars = keyboardTimedChars,
            )
        }
    }
}
