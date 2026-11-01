/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InputStatsBackupDataTest :
    StringSpec({
        "round trips through JSON" {
            val data =
                InputStatsBackupData(
                    daily =
                    listOf(
                        InputStatsBackupDaily(
                            day = "2026-11-01",
                            origin = InputStats.ORIGIN_RIME,
                            dimension = "luna_pinyin",
                            bucket = 2,
                            entryCount = 3,
                            charCount = 6,
                            activeMs = 5_000,
                            timedChars = 4,
                        ),
                    ),
                    schemas =
                    listOf(
                        InputStatsBackupSchema(
                            schemaId = "luna_pinyin",
                            schemaName = "Luna Pinyin",
                            entryCount = 3,
                            charCount = 6,
                            lastUsedAt = 1_760_000_000_000,
                        ),
                    ),
                )

            val json = Json { encodeDefaults = true }
            val encoded = json.encodeToString(data)
            json.decodeFromString<InputStatsBackupData>(encoded) shouldBe data
        }
    })
