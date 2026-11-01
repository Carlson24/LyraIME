/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "stats_daily",
    primaryKeys = ["day", "origin", "dimension", "bucket"],
)
data class InputStatsDailyEntity(
    @ColumnInfo(name = "day")
    val day: String,
    @ColumnInfo(name = "origin")
    val origin: String,
    @ColumnInfo(name = "dimension")
    val dimension: String,
    @ColumnInfo(name = "bucket")
    val bucket: Int,
    @ColumnInfo(name = "entry_count")
    val entryCount: Long,
    @ColumnInfo(name = "char_count")
    val charCount: Long,
    @ColumnInfo(name = "active_ms", defaultValue = "0")
    val activeMs: Long = 0,
    @ColumnInfo(name = "timed_chars", defaultValue = "0")
    val timedChars: Long = 0,
)

@Entity(tableName = "schema_usage")
data class InputStatsSchemaEntity(
    @PrimaryKey
    @ColumnInfo(name = "schema_id")
    val schemaId: String,
    @ColumnInfo(name = "schema_name")
    val schemaName: String,
    @ColumnInfo(name = "entry_count")
    val entryCount: Long,
    @ColumnInfo(name = "char_count")
    val charCount: Long,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long,
)
