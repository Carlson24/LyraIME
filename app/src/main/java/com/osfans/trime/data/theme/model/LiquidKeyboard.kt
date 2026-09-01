/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.symbol.LiquidData
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@Parcelize
@Serializable
data class LiquidKeyboard(
    val columns: Int = 6,
    val rows: Int = 5,
    val bottomPadding: Int? = null,
    val keyboards: List<Keyboard> = emptyList(),
) : Parcelable {
    @Parcelize
    @Serializable
    data class Keyboard(
        val id: String = "",
        val type: LiquidData.Type,
        val name: String = "",
        @Serializable(with = KeyItemListSerializer::class)
        val keys: List<KeyItem> = emptyList(),
    ) : Parcelable

    @Parcelize
    @Serializable
    data class KeyItem(
        val text: String = "",
        val altText: String = "",
    ) : Parcelable {
        constructor(text: String) : this(text, text)
    }
}

/**
 * 液态键盘按键列表序列化器。
 *
 * 支持在 `keys` 中混用两种写法：
 * - 纯字符串（如 `","`）：等价于 `{ text = ",", alt_text = "," }`
 * - 对象（如 `{ text = "!", alt_text = "！" }`）
 */
object KeyItemListSerializer : KSerializer<List<LiquidKeyboard.KeyItem>> {
    private val delegate = ListSerializer(LiquidKeyboard.KeyItem.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<LiquidKeyboard.KeyItem> {
        val input = decoder as? JsonDecoder
            ?: error("KeyItemListSerializer requires a JsonDecoder")
        val jsonArray = input.decodeJsonElement() as? JsonArray
            ?: error("Expected a JSON array for keys")
        return jsonArray.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> if (element.isString) LiquidKeyboard.KeyItem(element.content) else null
                is JsonObject -> input.json.decodeFromJsonElement(LiquidKeyboard.KeyItem.serializer(), element)
                else -> null
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<LiquidKeyboard.KeyItem>,
    ) {
        delegate.serialize(encoder, value)
    }
}
