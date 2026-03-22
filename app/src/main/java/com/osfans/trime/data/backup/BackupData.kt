/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val preferences: Map<String, BackupPreference>? = null,
    val clipboard: List<BackupBean>? = null,
    val collection: List<BackupBean>? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupPreference(
    val value: JsonElement,
    val type: PreferenceType,
)

@Serializable
enum class PreferenceType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    STRING,
    STRING_SET,
}

@Serializable
data class BackupBean(
    val text: String?,
    val html: String?,
    val type: String,
    val time: Long,
    val pinned: Boolean,
)
