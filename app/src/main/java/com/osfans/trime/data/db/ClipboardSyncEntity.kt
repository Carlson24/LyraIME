// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = ClipboardSyncEntity.TABLE_NAME)
data class ClipboardSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String = "",
    val ts: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val type: SyncEntryType = SyncEntryType.TEXT,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val localFilePath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val serverFileName: String? = null,
    val uuid: String = java.util.UUID.randomUUID().toString(),
) {
    companion object {
        const val TABLE_NAME = "t_clipboard_sync"
    }

    fun displayLabel(): String {
        if (type == SyncEntryType.TEXT) return text
        val rawName = fileName ?: serverFileName ?: text
        if (rawName.isBlank()) return ""
        val dotIndex = rawName.lastIndexOf('.')
        val base = if (dotIndex > 0) rawName.substring(0, dotIndex) else rawName
        val ext = if (dotIndex > 0 && dotIndex < rawName.length - 1) {
            rawName.substring(dotIndex + 1).uppercase()
        } else {
            "FILE"
        }
        return "$ext-$base"
    }
}

enum class SyncEntryType {
    TEXT,
    IMAGE,
    FILE,
}

enum class DownloadStatus {
    NONE,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

class ClipboardSyncConverters {
    @TypeConverter
    fun syncEntryTypeToInt(type: SyncEntryType): Int = type.ordinal

    @TypeConverter
    fun intToSyncEntryType(ordinal: Int): SyncEntryType = SyncEntryType.entries[ordinal]

    @TypeConverter
    fun downloadStatusToInt(status: DownloadStatus): Int = status.ordinal

    @TypeConverter
    fun intToDownloadStatus(ordinal: Int): DownloadStatus = DownloadStatus.entries[ordinal]
}
