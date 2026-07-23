/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.keyboard.KeyBehavior
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Parcelize
@Serializable
data class KeyboardRow(
    val height: Float = 0f,
    val split: Boolean = false,
    val keys: List<TextKeyboard.TextKey>,
) : Parcelable {
    fun isStructurallyIdenticalTo(other: KeyboardRow): Boolean {
        if (keys.size != other.keys.size) return false
        for (i in keys.indices) {
            val k1 = keys[i]
            val k2 = other.keys[i]
            if (k1.width != k2.width) return false
            if (k1.spacer != k2.spacer) return false
        }
        return true
    }
}

@Parcelize
@Serializable(with = TextKeyboardSerializer::class)
data class TextKeyboard(
    val name: String = "",
    val author: String = "",
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val asciiMode: Boolean = true,
    val resetAsciiMode: Boolean = false,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    val navbar: Boolean = false,
    val t9Mode: Boolean = false,
    val keyboardPaddingTop: Int = 0,
    val t9SidebarWidth: Float = 0.15f,
    val t9SidebarPosition: String = "left",
    val t9SidebarSpanRows: Int = 3,
    val t9SidebarShowItems: Int = 4,
    val t9SidebarSymbols: List<String> = emptyList(),
    val dynamicMode: Boolean = false,
    val dynamicOriginal: String = "",
    val rows: List<KeyboardRow> = emptyList(),
) : Parcelable {
    fun isStructurallyIdenticalTo(other: TextKeyboard): Boolean {
        if (this === other) return true
        if (keyboardHeight != other.keyboardHeight) return false
        if (keyboardHeightLand != other.keyboardHeightLand) return false
        if (horizontalGap != other.horizontalGap) return false
        if (verticalGap != other.verticalGap) return false
        if (keyboardPaddingTop != other.keyboardPaddingTop) return false
        if (landscapeSplitPercent != other.landscapeSplitPercent) return false
        if (roundCorner != other.roundCorner) return false
        if (keyBorder != other.keyBorder) return false
        if (t9Mode != other.t9Mode) return false
        if (dynamicMode != other.dynamicMode) return false
        if (rows.size != other.rows.size) return false
        for (i in rows.indices) {
            val r1 = rows[i]
            val r2 = other.rows[i]
            if (r1.height != r2.height) return false
            if (r1.split != r2.split) return false
            if (!r1.isStructurallyIdenticalTo(r2)) return false
        }
        return true
    }

    @Serializable
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Serializable
    enum class Align {
        LEFT,
        CENTER,
        RIGHT,
    }

    @Parcelize
    @Serializable
    data class LabelSegment(
        val text: String = "",
        val bold: Boolean = false,
        val color: String? = null,
        val scale: Float? = null,
        val align: Align? = null,
    ) : Parcelable

    @Parcelize
    @Serializable(with = TextKeySerializer::class)
    data class TextKey(
        val width: Float = 0f,
        val spacer: Boolean = false,
        val roundCorner: Float = -1f,
        val roundedCornerTopLeft: Float? = null,
        val roundedCornerTopRight: Float? = null,
        val roundedCornerBottomLeft: Float? = null,
        val roundedCornerBottomRight: Float? = null,
        val keyBorder: Int = -1,
        val keyBorderColor: String = "",
        val label: List<LabelSegment> = emptyList(),
        val labelSymbol: List<LabelSegment> = emptyList(),
        val hint: List<LabelSegment> = emptyList(),
        val click: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val hintTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keySymbolColor: String = "",
        @SerialName("hilited_key_text_color")
        val hlKeyTextColor: String = "",
        @SerialName("hilited_key_back_color")
        val hlKeyBackColor: String = "",
        @SerialName("hilited_key_symbol_color")
        val hlKeySymbolColor: String = "",
        val popup: List<String> = emptyList(),
        val dynamic: String = "",
        val behaviors: Map<KeyBehavior, String> = emptyMap(),
    ) : Parcelable
}

internal object TextKeySerializer : KSerializer<TextKeyboard.TextKey> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TextKey", PrimitiveKind.STRING)

    private fun behaviorKey(entry: KeyBehavior): String = entry.name.lowercase()

    private fun str(obj: JsonObject, key: String, default: String = ""): String = obj[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun flt(obj: JsonObject, key: String, default: Float = 0f): Float = obj[key]?.jsonPrimitive?.float ?: default

    private fun fltOrNull(obj: JsonObject, key: String): Float? = obj[key]?.jsonPrimitive?.float

    private fun int(obj: JsonObject, key: String, default: Int = 0): Int = obj[key]?.jsonPrimitive?.int ?: default

    private fun bool(obj: JsonObject, key: String, default: Boolean = false): Boolean = obj[key]?.jsonPrimitive?.boolean ?: default

    private fun strList(obj: JsonObject, key: String): List<String> = obj[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    private fun labelSegments(obj: JsonObject, key: String): List<TextKeyboard.LabelSegment> {
        val element = obj[key] ?: return emptyList()
        val arr = try {
            element.jsonArray
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to parse '$key': expected JSON array, got ${element::class.simpleName}: $element",
                e,
            )
        }
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            TextKeyboard.LabelSegment(
                text = o["text"]?.jsonPrimitive?.contentOrNull ?: "",
                bold = o["bold"]?.jsonPrimitive?.boolean ?: false,
                color = o["color"]?.jsonPrimitive?.contentOrNull,
                scale = o["scale"]?.jsonPrimitive?.float,
                align = o["align"]?.jsonPrimitive?.contentOrNull?.let { TextKeyboard.Align.valueOf(it.uppercase()) },
            )
        }
    }

    private fun labelSegmentsToJson(segs: List<TextKeyboard.LabelSegment>): kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonArray(
        segs.map { seg ->
            val m = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
            m["text"] = kotlinx.serialization.json.JsonPrimitive(seg.text)
            if (seg.bold) m["bold"] = kotlinx.serialization.json.JsonPrimitive(true)
            seg.color?.let { m["color"] = kotlinx.serialization.json.JsonPrimitive(it) }
            seg.scale?.let { m["scale"] = kotlinx.serialization.json.JsonPrimitive(it) }
            seg.align?.let { m["align"] = kotlinx.serialization.json.JsonPrimitive(it.name.lowercase()) }
            kotlinx.serialization.json.JsonObject(m)
        },
    )

    override fun serialize(encoder: Encoder, value: TextKeyboard.TextKey) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(serializeKey(value))
    }

    internal fun serializeKey(value: TextKeyboard.TextKey): JsonObject {
        val map = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        map["width"] = kotlinx.serialization.json.JsonPrimitive(value.width)
        map["spacer"] = kotlinx.serialization.json.JsonPrimitive(value.spacer)
        map["round_corner"] = kotlinx.serialization.json.JsonPrimitive(value.roundCorner)
        value.roundedCornerTopLeft?.let { map["round_corner_top_left"] = kotlinx.serialization.json.JsonPrimitive(it) }
        value.roundedCornerTopRight?.let { map["round_corner_top_right"] = kotlinx.serialization.json.JsonPrimitive(it) }
        value.roundedCornerBottomLeft?.let { map["round_corner_bottom_left"] = kotlinx.serialization.json.JsonPrimitive(it) }
        value.roundedCornerBottomRight?.let { map["round_corner_bottom_right"] = kotlinx.serialization.json.JsonPrimitive(it) }
        map["key_border"] = kotlinx.serialization.json.JsonPrimitive(value.keyBorder)
        map["key_border_color"] = kotlinx.serialization.json.JsonPrimitive(value.keyBorderColor)
        map["label"] = labelSegmentsToJson(value.label)
        map["label_symbol"] = labelSegmentsToJson(value.labelSymbol)
        map["hint"] = labelSegmentsToJson(value.hint)
        map["click"] = kotlinx.serialization.json.JsonPrimitive(value.click)
        map["send_bindings"] = kotlinx.serialization.json.JsonPrimitive(value.sendBindings)
        map["key_text_size"] = kotlinx.serialization.json.JsonPrimitive(value.keyTextSize)
        map["symbol_text_size"] = kotlinx.serialization.json.JsonPrimitive(value.symbolTextSize)
        map["hint_text_size"] = kotlinx.serialization.json.JsonPrimitive(value.hintTextSize)
        map["key_text_offset_x"] = kotlinx.serialization.json.JsonPrimitive(value.keyTextOffsetX)
        map["key_text_offset_y"] = kotlinx.serialization.json.JsonPrimitive(value.keyTextOffsetY)
        map["key_symbol_offset_x"] = kotlinx.serialization.json.JsonPrimitive(value.keySymbolOffsetX)
        map["key_symbol_offset_y"] = kotlinx.serialization.json.JsonPrimitive(value.keySymbolOffsetY)
        map["key_hint_offset_x"] = kotlinx.serialization.json.JsonPrimitive(value.keyHintOffsetX)
        map["key_hint_offset_y"] = kotlinx.serialization.json.JsonPrimitive(value.keyHintOffsetY)
        map["key_press_offset_x"] = kotlinx.serialization.json.JsonPrimitive(value.keyPressOffsetX)
        map["key_press_offset_y"] = kotlinx.serialization.json.JsonPrimitive(value.keyPressOffsetY)
        map["key_text_color"] = kotlinx.serialization.json.JsonPrimitive(value.keyTextColor)
        map["key_back_color"] = kotlinx.serialization.json.JsonPrimitive(value.keyBackColor)
        map["key_symbol_color"] = kotlinx.serialization.json.JsonPrimitive(value.keySymbolColor)
        map["hilited_key_text_color"] = kotlinx.serialization.json.JsonPrimitive(value.hlKeyTextColor)
        map["hilited_key_back_color"] = kotlinx.serialization.json.JsonPrimitive(value.hlKeyBackColor)
        map["hilited_key_symbol_color"] = kotlinx.serialization.json.JsonPrimitive(value.hlKeySymbolColor)
        map["popup"] = kotlinx.serialization.json.JsonArray(value.popup.map { kotlinx.serialization.json.JsonPrimitive(it) })
        map["dynamic"] = kotlinx.serialization.json.JsonPrimitive(value.dynamic)
        for ((b, action) in value.behaviors) {
            map[behaviorKey(b)] = kotlinx.serialization.json.JsonPrimitive(action)
        }
        return JsonObject(map)
    }

    override fun deserialize(decoder: Decoder): TextKeyboard.TextKey {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        return deserializeKey(obj)
    }

    internal fun deserializeKey(obj: JsonObject): TextKeyboard.TextKey {
        val behaviors = mutableMapOf<KeyBehavior, String>()
        for (entry in KeyBehavior.entries) {
            val key = behaviorKey(entry)
            val value = str(obj, key)
            if (value.isNotEmpty() || entry == KeyBehavior.CLICK) {
                behaviors[entry] = value
            }
        }

        return TextKeyboard.TextKey(
            width = flt(obj, "width"),
            spacer = bool(obj, "spacer"),
            roundCorner = flt(obj, "round_corner", -1f),
            roundedCornerTopLeft = fltOrNull(obj, "round_corner_top_left"),
            roundedCornerTopRight = fltOrNull(obj, "round_corner_top_right"),
            roundedCornerBottomLeft = fltOrNull(obj, "round_corner_bottom_left"),
            roundedCornerBottomRight = fltOrNull(obj, "round_corner_bottom_right"),
            keyBorder = int(obj, "key_border", -1),
            keyBorderColor = str(obj, "key_border_color"),
            label = labelSegments(obj, "label"),
            labelSymbol = labelSegments(obj, "label_symbol"),
            hint = labelSegments(obj, "hint"),
            click = str(obj, "click"),
            sendBindings = bool(obj, "send_bindings", true),
            keyTextSize = flt(obj, "key_text_size"),
            symbolTextSize = flt(obj, "symbol_text_size"),
            hintTextSize = flt(obj, "hint_text_size"),
            keyTextOffsetX = flt(obj, "key_text_offset_x"),
            keyTextOffsetY = flt(obj, "key_text_offset_y"),
            keySymbolOffsetX = flt(obj, "key_symbol_offset_x"),
            keySymbolOffsetY = flt(obj, "key_symbol_offset_y"),
            keyHintOffsetX = flt(obj, "key_hint_offset_x"),
            keyHintOffsetY = flt(obj, "key_hint_offset_y"),
            keyPressOffsetX = flt(obj, "key_press_offset_x"),
            keyPressOffsetY = flt(obj, "key_press_offset_y"),
            keyTextColor = str(obj, "key_text_color"),
            keyBackColor = str(obj, "key_back_color"),
            keySymbolColor = str(obj, "key_symbol_color"),
            hlKeyTextColor = str(obj, "hilited_key_text_color"),
            hlKeyBackColor = str(obj, "hilited_key_back_color"),
            hlKeySymbolColor = str(obj, "hilited_key_symbol_color"),
            popup = strList(obj, "popup"),
            dynamic = str(obj, "dynamic"),
            behaviors = behaviors,
        )
    }
}

internal object TextKeyboardSerializer : KSerializer<TextKeyboard> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TextKeyboard", PrimitiveKind.STRING)

    private fun str(obj: JsonObject, key: String, default: String = ""): String = obj[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun flt(obj: JsonObject, key: String, default: Float = 0f): Float = obj[key]?.jsonPrimitive?.float ?: default

    private fun int(obj: JsonObject, key: String, default: Int = 0): Int = obj[key]?.jsonPrimitive?.int ?: default

    private fun bool(obj: JsonObject, key: String, default: Boolean = false): Boolean = obj[key]?.jsonPrimitive?.boolean ?: default

    private fun strList(obj: JsonObject, key: String): List<String> = obj[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    override fun serialize(encoder: Encoder, value: TextKeyboard) {
        val jsonEncoder = encoder as JsonEncoder
        val map = linkedMapOf<String, JsonElement>()
        map["name"] = JsonPrimitive(value.name)
        map["author"] = JsonPrimitive(value.author)
        map["keyboard_height"] = JsonPrimitive(value.keyboardHeight)
        map["keyboard_height_land"] = JsonPrimitive(value.keyboardHeightLand)
        map["auto_height_index"] = JsonPrimitive(value.autoHeightIndex)
        map["horizontal_gap"] = JsonPrimitive(value.horizontalGap)
        map["vertical_gap"] = JsonPrimitive(value.verticalGap)
        map["round_corner"] = JsonPrimitive(value.roundCorner)
        map["key_border"] = JsonPrimitive(value.keyBorder)
        map["ascii_mode"] = JsonPrimitive(value.asciiMode)
        map["reset_ascii_mode"] = JsonPrimitive(value.resetAsciiMode)
        map["label_transform"] = JsonPrimitive(value.labelTransform.name.lowercase())
        map["lock"] = JsonPrimitive(value.lock)
        map["ascii_keyboard"] = JsonPrimitive(value.asciiKeyboard)
        map["landscape_keyboard"] = JsonPrimitive(value.landscapeKeyboard)
        map["landscape_split_percent"] = JsonPrimitive(value.landscapeSplitPercent)
        map["key_text_offset_x"] = JsonPrimitive(value.keyTextOffsetX)
        map["key_text_offset_y"] = JsonPrimitive(value.keyTextOffsetY)
        map["key_symbol_offset_x"] = JsonPrimitive(value.keySymbolOffsetX)
        map["key_symbol_offset_y"] = JsonPrimitive(value.keySymbolOffsetY)
        map["key_hint_offset_x"] = JsonPrimitive(value.keyHintOffsetX)
        map["key_hint_offset_y"] = JsonPrimitive(value.keyHintOffsetY)
        map["key_press_offset_x"] = JsonPrimitive(value.keyPressOffsetX)
        map["key_press_offset_y"] = JsonPrimitive(value.keyPressOffsetY)
        map["import_preset"] = JsonPrimitive(value.importPreset)
        map["navbar"] = JsonPrimitive(value.navbar)
        map["t9_mode"] = JsonPrimitive(value.t9Mode)
        map["keyboard_padding_top"] = JsonPrimitive(value.keyboardPaddingTop)
        map["t9_sidebar_width"] = JsonPrimitive(value.t9SidebarWidth)
        map["t9_sidebar_position"] = JsonPrimitive(value.t9SidebarPosition)
        map["t9_sidebar_span_rows"] = JsonPrimitive(value.t9SidebarSpanRows)
        map["t9_sidebar_show_items"] = JsonPrimitive(value.t9SidebarShowItems)
        map["t9_sidebar_symbols"] = JsonArray(value.t9SidebarSymbols.map { JsonPrimitive(it) })
        map["dynamic_mode"] = JsonPrimitive(value.dynamicMode)
        map["dynamic_original"] = JsonPrimitive(value.dynamicOriginal)
        map["rows"] = JsonArray(
            value.rows.map { row ->
                JsonObject(
                    buildMap {
                        put("height", JsonPrimitive(row.height))
                        put("split", JsonPrimitive(row.split))
                        put("keys", JsonArray(row.keys.map { TextKeySerializer.serializeKey(it) }))
                    },
                )
            },
        )
        jsonEncoder.encodeJsonElement(JsonObject(map))
    }

    override fun deserialize(decoder: Decoder): TextKeyboard {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val rowsArr = obj["rows"]?.jsonArray
        val rows = if (rowsArr != null) {
            rowsArr.mapIndexed { rowIdx, rowEl ->
                val rowObj = rowEl.jsonObject
                val keysArr = rowObj["keys"]?.jsonArray
                val keys = if (keysArr != null) {
                    keysArr.mapIndexed { keyIdx, keyEl ->
                        try {
                            TextKeySerializer.deserializeKey(keyEl.jsonObject)
                        } catch (e: Exception) {
                            throw IllegalArgumentException(
                                "${e.message}\nFailed to deserialize key at row $rowIdx, key $keyIdx: $keyEl", e,
                            )
                        }
                    }
                } else {
                    emptyList<TextKeyboard.TextKey>()
                }
                KeyboardRow(
                    height = flt(rowObj, "height"),
                    split = bool(rowObj, "split"),
                    keys = keys,
                )
            }
        } else {
            emptyList<KeyboardRow>()
        }

        return TextKeyboard(
            name = str(obj, "name"),
            author = str(obj, "author"),
            keyboardHeight = int(obj, "keyboard_height"),
            keyboardHeightLand = int(obj, "keyboard_height_land"),
            autoHeightIndex = int(obj, "auto_height_index", -1),
            horizontalGap = int(obj, "horizontal_gap"),
            verticalGap = int(obj, "vertical_gap"),
            roundCorner = flt(obj, "round_corner", -1f),
            keyBorder = int(obj, "key_border", -1),
            asciiMode = bool(obj, "ascii_mode", true),
            resetAsciiMode = bool(obj, "reset_ascii_mode"),
            labelTransform = str(obj, "label_transform", "none").let {
                TextKeyboard.LabelTransform.valueOf(it.uppercase())
            },
            lock = bool(obj, "lock"),
            asciiKeyboard = str(obj, "ascii_keyboard"),
            landscapeKeyboard = str(obj, "landscape_keyboard"),
            landscapeSplitPercent = int(obj, "landscape_split_percent"),
            keyTextOffsetX = flt(obj, "key_text_offset_x"),
            keyTextOffsetY = flt(obj, "key_text_offset_y"),
            keySymbolOffsetX = flt(obj, "key_symbol_offset_x"),
            keySymbolOffsetY = flt(obj, "key_symbol_offset_y"),
            keyHintOffsetX = flt(obj, "key_hint_offset_x"),
            keyHintOffsetY = flt(obj, "key_hint_offset_y"),
            keyPressOffsetX = flt(obj, "key_press_offset_x"),
            keyPressOffsetY = flt(obj, "key_press_offset_y"),
            importPreset = str(obj, "import_preset"),
            navbar = bool(obj, "navbar"),
            t9Mode = bool(obj, "t9_mode"),
            keyboardPaddingTop = int(obj, "keyboard_padding_top"),
            t9SidebarWidth = flt(obj, "t9_sidebar_width", 0.15f),
            t9SidebarPosition = str(obj, "t9_sidebar_position", "left"),
            t9SidebarSpanRows = int(obj, "t9_sidebar_span_rows", 3),
            t9SidebarShowItems = int(obj, "t9_sidebar_show_items", 4),
            t9SidebarSymbols = strList(obj, "t9_sidebar_symbols"),
            dynamicMode = bool(obj, "dynamic_mode"),
            dynamicOriginal = str(obj, "dynamic_original"),
            rows = rows,
        )
    }
}
