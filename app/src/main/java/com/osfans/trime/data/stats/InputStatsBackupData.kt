/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import kotlinx.serialization.Serializable

@Serializable
data class InputStatsBackupData(
    val daily: List<InputStatsBackupDaily> = emptyList(),
    val schemas: List<InputStatsBackupSchema> = emptyList(),
)

@Serializable
data class InputStatsBackupDaily(
    val day: String,
    val origin: String,
    val dimension: String,
    val bucket: Int,
    val entryCount: Long,
    val charCount: Long,
    val activeMs: Long = 0,
    val timedChars: Long = 0,
)

@Serializable
data class InputStatsBackupSchema(
    val schemaId: String,
    val schemaName: String,
    val entryCount: Long,
    val charCount: Long,
    val lastUsedAt: Long,
)
