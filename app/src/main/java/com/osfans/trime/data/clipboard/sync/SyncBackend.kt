// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import com.osfans.trime.data.clipboard.model.ClipboardContent
import com.osfans.trime.data.clipboard.model.HistoryQueryParams
import com.osfans.trime.data.clipboard.model.HistoryRecordDto
import com.osfans.trime.data.clipboard.model.HistoryRecordUpdateDto
import com.osfans.trime.data.clipboard.model.HistoryStatisticsDto
import com.osfans.trime.data.clipboard.model.ProfileDto
import com.osfans.trime.data.clipboard.model.ProgressInfo

enum class ServerType {
    SYNC_CLIPBOARD,
    WEBDAV,
    S3,
    ;

    companion object {
        fun fromString(value: String): ServerType = when (value.lowercase()) {
            "syncclipboard" -> SYNC_CLIPBOARD
            "webdav" -> WEBDAV
            "s3" -> S3
            else -> SYNC_CLIPBOARD
        }
    }
}

data class ServerConfig(
    val type: ServerType,
    val name: String? = null,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val region: String? = null,
    val bucketName: String? = null,
    val objectPrefix: String? = null,
    val forcePathStyle: Boolean? = null,
    val remotePath: String? = null,
)

data class PutContentOptions(
    val onProgress: ((ProgressInfo) -> Unit)? = null,
)

data class ServerInfo(
    val version: String = "",
    val serverTime: Long = 0,
)

interface ISyncClipboardAPI {
    suspend fun getClipboard(): ProfileDto
    suspend fun putClipboard(profile: ProfileDto)
    suspend fun downloadFile(
        fileName: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
    ): Pair<Boolean, String?>
    suspend fun putFile(
        fileName: String,
        localFilePath: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
    )
    suspend fun putContent(
        content: ClipboardContent,
        options: PutContentOptions? = null,
    )
    suspend fun getServerInfo(): ServerInfo
    suspend fun testConnection()
}

interface IHistoryAPI {
    suspend fun queryRecords(
        params: HistoryQueryParams,
    ): List<HistoryRecordDto>

    suspend fun getRecord(profileId: String): HistoryRecordDto
    suspend fun updateRecord(
        type: String,
        profileId: String,
        update: HistoryRecordUpdateDto,
    ): HistoryRecordDto

    suspend fun downloadData(
        profileId: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
    ): Pair<Boolean, String?>

    suspend fun uploadRecord(
        record: HistoryRecordDto,
        fileUri: String?,
        progressCallback: ((Long, Long) -> Unit)? = null,
    ): HistoryRecordDto

    suspend fun getStatistics(): HistoryStatisticsDto
    suspend fun getServerTime(): Long
}
