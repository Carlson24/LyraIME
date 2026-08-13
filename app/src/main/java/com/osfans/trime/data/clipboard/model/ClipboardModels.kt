// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.model

import com.osfans.trime.data.clipboard.util.HashUtils
import java.io.File

enum class ClipboardContentType {
    Text,
    Image,
    File,
    Group,
    ;

    companion object {
        fun fromString(value: String): ClipboardContentType = when (value.lowercase()) {
            "text" -> Text
            "image" -> Image
            "file" -> File
            "group" -> Group
            else -> Text
        }
    }
}

@kotlinx.serialization.Serializable
data class ProfileDto(
    val type: String = "Text",
    val hash: String? = null,
    val text: String = "",
    val hasData: Boolean = false,
    val dataName: String? = null,
    val size: Long? = null,
)

data class ClipboardContent(
    val type: ClipboardContentType = ClipboardContentType.Text,
    val text: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val profileHash: String? = null,
    val localClipboardHash: String? = null,
    val fileData: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val hasData: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipboardContent) return false
        return type == other.type &&
            text == other.text &&
            fileUri == other.fileUri &&
            fileName == other.fileName &&
            fileSize == other.fileSize &&
            profileHash == other.profileHash &&
            localClipboardHash == other.localClipboardHash &&
            fileData.contentEquals(other.fileData) &&
            hasData == other.hasData
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + (fileUri?.hashCode() ?: 0)
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (fileSize?.hashCode() ?: 0)
        result = 31 * result + (profileHash?.hashCode() ?: 0)
        result = 31 * result + (localClipboardHash?.hashCode() ?: 0)
        result = 31 * result + (fileData?.contentHashCode() ?: 0)
        result = 31 * result + hasData.hashCode()
        return result
    }
}

fun contentToProfileDto(content: ClipboardContent): ProfileDto {
    val hash = content.profileHash
        ?: calculateContentHash(content)
    return when (content.type) {
        ClipboardContentType.Text -> ProfileDto(
            type = "Text",
            hash = hash,
            text = content.text,
            hasData = content.fileUri != null,
            dataName = content.fileName,
            size = content.fileSize,
        )

        ClipboardContentType.Image -> ProfileDto(
            type = "Image",
            hash = hash,
            text = content.text.ifEmpty { "[图片]" },
            hasData = true,
            dataName = content.fileName,
            size = content.fileSize,
        )

        ClipboardContentType.File -> ProfileDto(
            type = "File",
            hash = hash,
            text = content.text.ifEmpty { content.fileName ?: "[文件]" },
            hasData = true,
            dataName = content.fileName,
            size = content.fileSize,
        )

        ClipboardContentType.Group -> ProfileDto(
            type = "Group",
            hash = hash,
            text = content.text.ifEmpty { "[文件组]" },
            hasData = true,
            dataName = content.fileName,
            size = content.fileSize,
        )
    }
}

fun profileDtoToContent(dto: ProfileDto): ClipboardContent {
    val type = ClipboardContentType.fromString(dto.type)
    return ClipboardContent(
        type = type,
        text = dto.text,
        fileName = if (dto.hasData) dto.dataName else null,
        fileSize = if (dto.hasData) dto.size else (dto.size ?: dto.text.length.toLong()),
        profileHash = dto.hash,
        timestamp = System.currentTimeMillis(),
        hasData = dto.hasData,
    )
}

fun calculateContentHash(content: ClipboardContent): String? {
    return when (content.type) {
        ClipboardContentType.Text -> {
            if (content.text.isEmpty()) {
                null
            } else {
                HashUtils.calculateTextHash(content.text)
            }
        }

        ClipboardContentType.Image, ClipboardContentType.File -> {
            val uri = content.fileUri ?: return null
            val file = File(uri)
            if (!file.exists()) return null
            val fileHash = HashUtils.calculateFileHash(file) ?: return null
            val name = content.fileName
                ?: file.name
                ?: return null
            HashUtils.calculateFileProfileHash(name, fileHash)
        }

        ClipboardContentType.Group -> content.profileHash
    }
}
