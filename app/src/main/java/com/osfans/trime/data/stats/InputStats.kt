/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

object InputStats {
    const val ORIGIN_RIME = "rime"
    const val ORIGIN_VOICE_BUILTIN = "voice_builtin"
    const val ORIGIN_VOICE_AIDL = "voice_aidl"

    const val UNKNOWN_SCHEMA_ID = "unknown"
}

enum class InputStatsVoiceSource(
    val origin: String,
) {
    BUILTIN(InputStats.ORIGIN_VOICE_BUILTIN),
    AIDL(InputStats.ORIGIN_VOICE_AIDL),
}
