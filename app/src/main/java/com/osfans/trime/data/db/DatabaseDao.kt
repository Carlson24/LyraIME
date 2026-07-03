/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DatabaseDao {
    @Insert
    suspend fun insert(bean: DatabaseBean): Long

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET text=:newText WHERE id=:id")
    suspend fun updateText(
        id: Int,
        newText: String,
    )

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET pinned=:pinned WHERE id=:id")
    suspend fun updatePinned(
        id: Int,
        pinned: Boolean,
    )

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET time=:timestamp WHERE id=:id")
    suspend fun updateTime(
        id: Int,
        timestamp: Long,
    )

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET sensitive=:sensitive WHERE id=:id")
    suspend fun updateSensitive(
        id: Int,
        sensitive: Boolean,
    )

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME}")
    suspend fun deleteAll()

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE NOT pinned")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE time<:timestamp AND pinned=0 AND deleted=0")
    suspend fun deleteUnpinnedEarlierThan(timestamp: Long)

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE deleted=0 ORDER BY pinned DESC, time DESC")
    fun allEntries(): PagingSource<Int, DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=1 AND deleted=0 ORDER BY time DESC")
    fun favoriteEntries(): PagingSource<Int, DatabaseBean>

    @Query(
        "SELECT * FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND text NOT LIKE 'content://%' AND text NOT LIKE 'file://%' AND deleted=0 " +
            "ORDER BY pinned DESC, time DESC",
    )
    fun textEntriesBySource(source: String): PagingSource<Int, DatabaseBean>

    @Query(
        "SELECT * FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND deleted=0 " +
            "ORDER BY pinned DESC, time DESC",
    )
    fun entriesBySource(source: String): PagingSource<Int, DatabaseBean>

    @Query(
        "SELECT * FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE (text LIKE 'content://%' OR text LIKE 'file://%') AND deleted=0 " +
            "ORDER BY pinned DESC, time DESC",
    )
    fun mediaEntries(): PagingSource<Int, DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id AND deleted=0 LIMIT 1")
    suspend fun get(id: Int): DatabaseBean?

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE rowId=:rowId AND deleted=0 LIMIT 1")
    suspend fun get(rowId: Long): DatabaseBean?

    @Query("SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0 AND deleted=0)")
    suspend fun haveUnpinned(): Boolean

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0 AND deleted=0")
    suspend fun getAllUnpinned(): List<DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text AND deleted=0 LIMIT 1")
    suspend fun find(text: String): DatabaseBean?

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text AND source='remote' AND deleted=0 LIMIT 1")
    suspend fun findRemoteText(text: String): DatabaseBean?

    @Query("SELECT COUNT(*) FROM ${DatabaseBean.TABLE_NAME} WHERE deleted=0")
    suspend fun itemCount(): Int

    @Query("SELECT id FROM ${DatabaseBean.TABLE_NAME} WHERE deleted=0")
    suspend fun findAllIds(): IntArray

    @Query("SELECT id FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0 AND deleted=0")
    suspend fun findUnpinnedIds(): IntArray

    @Query("SELECT id FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=1 AND deleted=0")
    suspend fun findPinnedIds(): IntArray

    @Query(
        "SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND text NOT LIKE 'content://%' AND text NOT LIKE 'file://%' " +
            "AND pinned=0 AND deleted=0)",
    )
    suspend fun haveUnpinnedTextEntriesBySource(source: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND pinned=0 AND deleted=0)",
    )
    suspend fun haveUnpinnedEntriesBySource(source: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE (text LIKE 'content://%' OR text LIKE 'file://%') AND pinned=0 AND deleted=0)",
    )
    suspend fun haveUnpinnedMediaEntries(): Boolean

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND text NOT LIKE 'content://%' AND text NOT LIKE 'file://%' AND deleted=0",
    )
    suspend fun findAllTextEntryIdsBySource(source: String): IntArray

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND deleted=0",
    )
    suspend fun findAllEntryIdsBySource(source: String): IntArray

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND text NOT LIKE 'content://%' AND text NOT LIKE 'file://%' " +
            "AND pinned=0 AND deleted=0",
    )
    suspend fun findUnpinnedTextEntryIdsBySource(source: String): IntArray

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE source=:source AND pinned=0 AND deleted=0",
    )
    suspend fun findUnpinnedEntryIdsBySource(source: String): IntArray

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE (text LIKE 'content://%' OR text LIKE 'file://%') AND deleted=0",
    )
    suspend fun findAllMediaEntryIds(): IntArray

    @Query(
        "SELECT id FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE (text LIKE 'content://%' OR text LIKE 'file://%') AND pinned=0 AND deleted=0",
    )
    suspend fun findUnpinnedMediaEntryIds(): IntArray

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET deleted=1 WHERE id in (:ids)")
    suspend fun markAsDeleted(vararg ids: Int)

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET DELETED=1 WHERE time<:timestamp AND pinned=0 AND deleted=0")
    suspend fun markUnpinnedAsDeletedEarlierThan(timestamp: Long)

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET deleted=0 WHERE id in (:ids) AND deleted=1")
    suspend fun undoDelete(vararg ids: Int)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE deleted=1")
    suspend fun realDelete()

    @Query(
        "SELECT * FROM ${DatabaseBean.TABLE_NAME} " +
            "WHERE deleted=0 AND text NOT LIKE 'content://%' AND text NOT LIKE 'file://%' " +
            "AND text LIKE '%' || :query || '%' " +
            "ORDER BY pinned DESC, time DESC",
    )
    fun searchEntries(query: String): PagingSource<Int, DatabaseBean>
}
