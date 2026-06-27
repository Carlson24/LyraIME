// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HistoryRecordDto(
    val hash: String,
    val type: String,
    val text: String? = null,
    val createTime: String? = null,
    val lastModified: String? = null,
    val lastAccessed: String? = null,
    val starred: Boolean? = null,
    val pinned: Boolean? = null,
    val size: Long? = null,
    val hasData: Boolean? = null,
    val version: Int? = null,
    val isDeleted: Boolean? = null,
)

@Serializable
data class HistoryRecordUpdateDto(
    val starred: Boolean? = null,
    val pinned: Boolean? = null,
    val isDeleted: Boolean? = null,
    val text: String? = null,
    val lastAccessed: String? = null,
    val version: Int? = null,
)

@Serializable
data class HistoryStatisticsDto(
    val totalRecords: Long = 0,
    val totalSize: Long = 0,
    val textCount: Long = 0,
    val imageCount: Long = 0,
    val fileCount: Long = 0,
    val groupCount: Long = 0,
)

data class HistoryQueryParams(
    val page: Int? = null,
    val before: String? = null,
    val after: String? = null,
    val modifiedAfter: String? = null,
    val types: Int? = null,
    val searchText: String? = null,
    val starred: Boolean? = null,
    val sortByLastAccessed: Boolean? = null,
)

object ProfileTypeFilter {
    const val TEXT = 1
    const val IMAGE = 2
    const val FILE = 4
    const val GROUP = 8
    const val ALL = 15
    const val FILE_AND_GROUP = 12
}

data class ProgressInfo(
    val bytesDownloaded: Long,
    val totalBytes: Long,
)
