// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard

import android.view.inputmethod.InputConnection
import com.osfans.trime.data.db.ClipboardSyncDao
import com.osfans.trime.data.db.ClipboardSyncEntity
import com.osfans.trime.data.db.DownloadStatus
import com.osfans.trime.data.db.SyncEntryType
import timber.log.Timber

class ClipboardHistoryStore(private val dao: ClipboardSyncDao) {

    companion object {
        private const val TAG = "ClipboardHistoryStore"
        private const val MAX_HISTORY = 200
    }

    suspend fun getAll(): List<ClipboardSyncEntity> = dao.getAll()

    suspend fun getPinned(): List<ClipboardSyncEntity> = dao.getPinned()

    suspend fun getHistory(): List<ClipboardSyncEntity> = dao.getHistory()

    suspend fun totalCount(): Int = dao.totalCount()

    suspend fun clearFileEntries() = dao.clearFileEntries()

    suspend fun addFromClipboard(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        try {
            val history = dao.getHistory()
            if (history.firstOrNull()?.text == trimmed) return
            val entry = ClipboardSyncEntity(
                text = trimmed,
                ts = System.currentTimeMillis(),
                pinned = false,
                type = SyncEntryType.TEXT,
            )
            dao.insert(entry)
            pruneHistory()
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "addFromClipboard failed")
        }
    }

    suspend fun togglePin(uuid: String): Boolean = try {
        val entry = dao.getByUuid(uuid) ?: return false
        dao.updatePinned(uuid, !entry.pinned)
        !entry.pinned
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "togglePin failed")
        false
    }

    suspend fun deleteHistoryBefore(cutoffEpochMs: Long): Int = try {
        dao.deleteHistoryBefore(cutoffEpochMs)
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "deleteHistoryBefore failed")
        0
    }

    suspend fun clearAllNonPinned(): Int = try {
        val count = dao.getHistory().size
        dao.deleteAllNonPinned()
        count
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "clearAllNonPinned failed")
        0
    }

    suspend fun deleteHistoryByUuid(uuid: String): Boolean = try {
        dao.deleteByUuid(uuid)
        true
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "deleteHistoryByUuid failed")
        false
    }

    fun pasteInto(ic: InputConnection?, text: String) {
        if (ic == null) return
        try {
            ic.commitText(text, 1)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "commitText failed")
        }
    }

    suspend fun addFileEntry(
        type: SyncEntryType,
        fileName: String,
        serverFileName: String,
        fileSize: Long? = null,
        mimeType: String? = null,
        localFilePath: String? = null,
        downloadStatus: DownloadStatus = DownloadStatus.NONE,
    ): Boolean {
        try {
            dao.clearFileEntries()
            val entry = ClipboardSyncEntity(
                text = "",
                ts = System.currentTimeMillis(),
                pinned = false,
                type = type,
                fileName = fileName,
                serverFileName = serverFileName,
                fileSize = fileSize,
                mimeType = mimeType,
                localFilePath = localFilePath,
                downloadStatus = downloadStatus,
            )
            dao.insert(entry)
            pruneHistory()
            return true
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "addFileEntry failed")
            return false
        }
    }

    suspend fun updateFileEntry(
        uuid: String,
        localFilePath: String?,
        downloadStatus: DownloadStatus,
    ): Boolean = try {
        dao.updateFileEntry(uuid, localFilePath, downloadStatus)
        true
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "updateFileEntry failed")
        false
    }

    suspend fun getEntryByUuid(uuid: String): ClipboardSyncEntity? = try {
        dao.getByUuid(uuid)
    } catch (t: Throwable) {
        Timber.tag(TAG).e(t, "getEntryByUuid failed")
        null
    }

    private suspend fun pruneHistory() {
        try {
            val history = dao.getHistory()
            if (history.size > MAX_HISTORY) {
                val toRemove = history.drop(MAX_HISTORY)
                for (entry in toRemove) {
                    dao.deleteByUuid(entry.uuid)
                }
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "pruneHistory failed")
        }
    }
}
