// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClipboardSyncDao {
    @Insert
    suspend fun insert(entry: ClipboardSyncEntity): Long

    @Query("SELECT * FROM ${ClipboardSyncEntity.TABLE_NAME} ORDER BY pinned DESC, ts DESC")
    suspend fun getAll(): List<ClipboardSyncEntity>

    @Query("SELECT * FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE pinned=1 ORDER BY ts DESC")
    suspend fun getPinned(): List<ClipboardSyncEntity>

    @Query("SELECT * FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE pinned=0 ORDER BY ts DESC")
    suspend fun getHistory(): List<ClipboardSyncEntity>

    @Query("SELECT COUNT(*) FROM ${ClipboardSyncEntity.TABLE_NAME}")
    suspend fun totalCount(): Int

    @Query("DELETE FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE type!=0")
    suspend fun clearFileEntries()

    @Query("DELETE FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE pinned=0")
    suspend fun deleteAllNonPinned()

    @Query("DELETE FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE pinned=0 AND ts<:cutoff")
    suspend fun deleteHistoryBefore(cutoff: Long): Int

    @Query("DELETE FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE uuid=:uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT * FROM ${ClipboardSyncEntity.TABLE_NAME} WHERE uuid=:uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ClipboardSyncEntity?

    @Query("UPDATE ${ClipboardSyncEntity.TABLE_NAME} SET pinned=:pinned, ts=:ts WHERE uuid=:uuid")
    suspend fun updatePinned(uuid: String, pinned: Boolean, ts: Long = System.currentTimeMillis())

    @Query(
        """UPDATE ${ClipboardSyncEntity.TABLE_NAME}
        SET localFilePath=:localFilePath, downloadStatus=:downloadStatus
        WHERE uuid=:uuid""",
    )
    suspend fun updateFileEntry(
        uuid: String,
        localFilePath: String?,
        downloadStatus: DownloadStatus,
    )
}
