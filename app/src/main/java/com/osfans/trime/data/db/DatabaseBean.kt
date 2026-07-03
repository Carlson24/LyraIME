// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = DatabaseBean.TABLE_NAME)
data class DatabaseBean(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    @ColumnInfo(defaultValue = "")
    val originalText: String = "",
    @ColumnInfo(defaultValue = "")
    val originalRootUri: String = "",
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "-1")
    val time: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = ClipDescription.MIMETYPE_TEXT_PLAIN)
    val type: String = ClipDescription.MIMETYPE_TEXT_PLAIN,
    @ColumnInfo(defaultValue = "local")
    val source: String = SOURCE_LOCAL,
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sensitive: Boolean = false,
) {
    companion object {
        const val TABLE_NAME = "t_data"
        const val SOURCE_LOCAL = "local"
        const val SOURCE_REMOTE = "remote"
        const val BULLET = "\u2022"

        private val IS_SENSITIVE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }

        fun fromClipData(
            clipData: ClipData,
            transformer: ((String) -> String)? = null,
        ): DatabaseBean? {
            val desc = clipData.description
            val item = clipData.getItemAt(0) ?: return null
            val str = item.text?.toString()
                ?: item.uri?.toString()
                ?: return null
            val sensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                desc.extras?.getBoolean(IS_SENSITIVE) ?: false
            } else {
                false
            }
            return DatabaseBean(
                text = if (transformer != null) transformer(str) else str,
                time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    desc.timestamp
                } else {
                    System.currentTimeMillis()
                },
                type = desc.getMimeType(0),
                sensitive = sensitive,
            )
        }

        fun fromInputConnection(text: String): DatabaseBean = DatabaseBean(text = text)
    }

    fun isUriEntry(): Boolean = text.startsWith("content://") || text.startsWith("file://")

    fun isRemoteTextEntry(): Boolean = source == SOURCE_REMOTE && !isUriEntry()
}
