/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object RawJsonStringSerializer : KSerializer<String?> {
    private val json = Json { isLenient = true }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "RawJsonString",
        PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as JsonEncoder
        if (value == null) {
            jsonEncoder.encodeJsonElement(JsonNull)
            return
        }
        try {
            jsonEncoder.encodeJsonElement(json.parseToJsonElement(value))
        } catch (_: Exception) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value))
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonNull -> null
            else -> element.toString()
        }
    }
}

@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val preferences: Map<String, BackupPreference>? = null,
    val clipboard: List<BackupBean>? = null,
    val wanxiangPrefs: Map<String, BackupPreference>? = null,
    @Serializable(with = RawJsonStringSerializer::class)
    val customTasks: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class BackupPreference(
    val value: JsonElement,
    val type: PreferenceType,
    val encoded: Boolean = false,
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
    val type: String,
    val time: Long,
    val pinned: Boolean,
)
