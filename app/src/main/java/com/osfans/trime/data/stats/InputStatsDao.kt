/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InputStatsDao {
    @Query("SELECT * FROM stats_daily")
    fun observeDailyStats(): Flow<List<InputStatsDailyEntity>>

    @Query("SELECT * FROM schema_usage ORDER BY char_count DESC, last_used_at DESC")
    fun observeSchemaUsage(): Flow<List<InputStatsSchemaEntity>>

    @Query("SELECT * FROM stats_daily")
    suspend fun getAllDaily(): List<InputStatsDailyEntity>

    @Query("SELECT * FROM schema_usage")
    suspend fun getAllSchemas(): List<InputStatsSchemaEntity>

    @Query(
        """
        UPDATE stats_daily
        SET entry_count = entry_count + :entryCount,
            char_count = char_count + :charCount,
            active_ms = active_ms + :activeMs,
            timed_chars = timed_chars + :timedChars
        WHERE day = :day
          AND origin = :origin
          AND dimension = :dimension
          AND bucket = :bucket
        """,
    )
    suspend fun updateDaily(
        day: String,
        origin: String,
        dimension: String,
        bucket: Int,
        entryCount: Long,
        charCount: Long,
        activeMs: Long,
        timedChars: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDaily(entity: InputStatsDailyEntity): Long

    @Query(
        """
        UPDATE schema_usage
        SET schema_name = :schemaName,
            entry_count = entry_count + :entryCount,
            char_count = char_count + :charCount,
            last_used_at = MAX(last_used_at, :lastUsedAt)
        WHERE schema_id = :schemaId
        """,
    )
    suspend fun updateSchema(
        schemaId: String,
        schemaName: String,
        entryCount: Long,
        charCount: Long,
        lastUsedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSchema(entity: InputStatsSchemaEntity): Long

    @Query("DELETE FROM stats_daily")
    suspend fun deleteAllDaily()

    @Query("DELETE FROM schema_usage")
    suspend fun deleteAllSchemas()
}
