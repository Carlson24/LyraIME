/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 共享的 LabelSpec 解析与序列化逻辑，供布局 [TextKeyboard.TextKey] 与 [PresetKey] 复用。 */
internal object LabelSpecParser {
    /**
     * 解析 LabelSpec JSON 元素（分段数组 / 对象 / 可选字符串）为标签分段列表。
     *
     * @param element JSON 元素，null 时返回空列表
     * @param key 出错时用于报错的字段名
     * @param allowString 是否允许顶层纯字符串（PresetKey 的向后兼容写法），空字符串视为无标签
     */
    fun parseElement(
        element: JsonElement?,
        key: String,
        allowString: Boolean = false,
    ): List<TextKeyboard.LabelSegment> {
        if (element == null) return emptyList()

        if (element is JsonObject) {
            return parseObject(element.jsonObject)
        }

        if (element is JsonPrimitive && allowString) {
            val text = element.contentOrNull
            return if (text.isNullOrEmpty()) emptyList() else listOf(TextKeyboard.LabelSegment(text = text))
        }

        val arr = try {
            element.jsonArray
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to parse '$key': expected JSON array or object, got ${element::class.simpleName}: $element",
                e,
            )
        }
        return arr.flatMap { el -> expandSegment(el.jsonObject) }
    }

    /** 对象形式：`{ text = "...", color = "...", align = "...", ... }`（text 可为数组 + 并行样式数组）。 */
    private fun parseObject(o: JsonObject): List<TextKeyboard.LabelSegment> {
        val textEl = o["text"] ?: return emptyList()
        val hasArrayStyles = listOf(o["color"], o["align"], o["bold"], o["scale"], o["valign"]).any { it is JsonArray }

        val texts: List<String> = when (textEl) {
            is JsonPrimitive -> {
                if (hasArrayStyles) {
                    throw IllegalArgumentException(
                        "label object: 'text' must be an array when style arrays (color/align/bold/scale/valign) are used",
                    )
                }
                textEl.content.map { it.toString() }
            }

            is JsonArray -> textEl.mapNotNull { it.jsonPrimitive.contentOrNull }

            else -> return emptyList()
        }
        if (texts.isEmpty()) return emptyList()

        return segmentList(o, texts)
    }

    /** 分段数组中的一个条目。 */
    private fun expandSegment(o: JsonObject): List<TextKeyboard.LabelSegment> {
        val textEl = o["text"]
        val hasArrayStyles = listOf(o["color"], o["align"], o["bold"], o["scale"], o["valign"]).any { it is JsonArray }

        val texts: List<String> = when (textEl) {
            null -> listOf("")

            is JsonPrimitive -> {
                if (hasArrayStyles) {
                    throw IllegalArgumentException(
                        "label segment entry: 'text' must be an array when style arrays are used",
                    )
                }
                listOf(textEl.contentOrNull ?: "")
            }

            is JsonArray -> textEl.mapNotNull { it.jsonPrimitive.contentOrNull }

            else -> listOf("")
        }
        if (texts.isEmpty()) return emptyList()

        return segmentList(o, texts)
    }

    /** 按索引展开并行样式（单值样式对全部分段生效）。 */
    private fun segmentList(o: JsonObject, texts: List<String>): List<TextKeyboard.LabelSegment> = texts.mapIndexed { i, txt ->
        TextKeyboard.LabelSegment(
            text = txt,
            bold = styleAt(o["bold"], i) { it.boolean } ?: false,
            color = styleAt(o["color"], i) { it.contentOrNull },
            scale = styleAt(o["scale"], i) { it.float },
            align = styleAt(o["align"], i) { it.contentOrNull }
                ?.let { TextKeyboard.Align.valueOf(it.uppercase()) },
            valign = styleAt(o["valign"], i) { it.contentOrNull }
                ?.let { TextKeyboard.VerticalAlign.valueOf(it.uppercase()) },
        )
    }

    private inline fun <T> styleAt(el: JsonElement?, i: Int, extract: (JsonPrimitive) -> T): T? = when (el) {
        is JsonArray -> el.getOrNull(i)?.jsonPrimitive?.let(extract)
        is JsonPrimitive -> el.let(extract)
        else -> null
    }

    /** 将分段列表编码为 JSON 数组（分段对象）。 */
    fun toJson(segs: List<TextKeyboard.LabelSegment>): JsonElement = JsonArray(
        segs.map { seg ->
            val m = mutableMapOf<String, JsonElement>()
            m["text"] = JsonPrimitive(seg.text)
            if (seg.bold) m["bold"] = JsonPrimitive(true)
            seg.color?.let { m["color"] = JsonPrimitive(it) }
            seg.scale?.let { m["scale"] = JsonPrimitive(it) }
            seg.align?.let { m["align"] = JsonPrimitive(it.name.lowercase()) }
            seg.valign?.let { m["valign"] = JsonPrimitive(it.name.lowercase()) }
            JsonObject(m)
        },
    )

    /** 单段纯文本（无任何样式）标签。 */
    fun isPlain(segs: List<TextKeyboard.LabelSegment>): Boolean = segs.size == 1 &&
        !segs[0].bold &&
        segs[0].color == null &&
        segs[0].scale == null &&
        segs[0].align == null &&
        segs[0].valign == null
}

/** [PresetKey] 等字段的 LabelSpec 序列化器：接受纯字符串 / 对象 / 分段数组，编码时纯文本还原为字符串。 */
internal object LabelSpecSerializer : KSerializer<List<TextKeyboard.LabelSegment>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LabelSpec", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<TextKeyboard.LabelSegment> {
        val jsonDecoder = decoder as JsonDecoder
        return LabelSpecParser.parseElement(jsonDecoder.decodeJsonElement(), "label", allowString = true)
    }

    override fun serialize(encoder: Encoder, value: List<TextKeyboard.LabelSegment>) {
        val jsonEncoder = encoder as JsonEncoder
        val element = if (LabelSpecParser.isPlain(value)) JsonPrimitive(value[0].text) else LabelSpecParser.toJson(value)
        jsonEncoder.encodeJsonElement(element)
    }
}
