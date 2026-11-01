/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputStatsSnapshotTest :
    StringSpec({
        "calculates keyboard speed from active time and timed characters" {
            val snapshot =
                InputStatsSnapshot.build(
                    daily =
                    listOf(
                        InputStatsDailyEntity(
                            day = "2026-11-01",
                            origin = InputStats.ORIGIN_RIME,
                            dimension = "luna_pinyin",
                            bucket = 2,
                            entryCount = 2,
                            charCount = 4,
                            activeMs = 5_000,
                            timedChars = 3,
                        ),
                        InputStatsDailyEntity(
                            day = "2026-11-01",
                            origin = InputStats.ORIGIN_RIME,
                            dimension = "luna_pinyin",
                            bucket = 1,
                            entryCount = 1,
                            charCount = 2,
                            activeMs = 5_000,
                            timedChars = 2,
                        ),
                    ),
                    schemas = emptyList(),
                    today = "2026-11-01",
                )

            snapshot.keyboardActiveMs shouldBe 10_000
            snapshot.keyboardTimedChars shouldBe 5
            snapshot.keyboardSpeedCharsPerMinute shouldBe 30.0
        }

        "returns null speed without active typing time" {
            val snapshot =
                InputStatsSnapshot.build(
                    daily =
                    listOf(
                        InputStatsDailyEntity(
                            day = "2026-11-01",
                            origin = InputStats.ORIGIN_RIME,
                            dimension = "luna_pinyin",
                            bucket = 1,
                            entryCount = 1,
                            charCount = 1,
                        ),
                    ),
                    schemas = emptyList(),
                    today = "2026-11-01",
                )

            snapshot.keyboardSpeedCharsPerMinute shouldBe null
        }

        "ignores voice rows when calculating keyboard speed" {
            val snapshot =
                InputStatsSnapshot.build(
                    daily =
                    listOf(
                        InputStatsDailyEntity(
                            day = "2026-11-01",
                            origin = InputStats.ORIGIN_VOICE_BUILTIN,
                            dimension = InputStats.ORIGIN_VOICE_BUILTIN,
                            bucket = 2,
                            entryCount = 1,
                            charCount = 2,
                            activeMs = 5_000,
                            timedChars = 2,
                        ),
                    ),
                    schemas = emptyList(),
                    today = "2026-11-01",
                )

            snapshot.keyboardSpeedCharsPerMinute shouldBe null
        }
    })
