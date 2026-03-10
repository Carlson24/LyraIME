/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.splitWithSurrogates
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.enum
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class LiquidKeyboard(
    val singleWidth: Int,
    val keyHeight: Int,
    val marginX: Float,
    val fixedKeyBar: KeyBar,
    val keyboards: List<Keyboard>,
) : Parcelable {
    @Parcelize
    data class EdgeInsets(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
    ) : Parcelable {
        companion object {
            fun all(value: Float): EdgeInsets = EdgeInsets(value, value, value, value)
        }
    }

    @Parcelize
    data class KeyBar(
        val keys: List<FixedKeyItem>,
        val position: Position,
    ) : Parcelable {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    @Parcelize
    data class FixedKeyItem(
        val click: String = "",
        val label: String = "",
        val width: Float? = null,
        val height: Float? = null,
        val margin: EdgeInsets? = null,
        val padding: EdgeInsets? = null,
    ) : Parcelable

    @Parcelize
    data class Keyboard(
        val id: String,
        val type: LiquidData.Type,
        val name: String,
        val keys: List<KeyItem>,
    ) : Parcelable

    @Parcelize
    data class KeyItem(
        val text: String,
        val altText: String,
    ) : Parcelable {
        constructor(text: String) : this(text, text)
    }

    companion object {
        private fun parseEdgeInsets(node: Node?): EdgeInsets? = when (node) {
            is Node.Scalar -> {
                // 单个值格式：所有方向都使用这个值
                node.string.toFloatOrNull()?.let { EdgeInsets.all(it) }
            }
            is Node.Sequence -> {
                // 数组格式：[left, top, right, bottom]
                val floats = node.mapNotNull { (it as? Node.Scalar)?.string?.toFloatOrNull() }
                when (floats.size) {
                    1 -> EdgeInsets.all(floats[0])
                    2 -> EdgeInsets(floats[0], floats[1], floats[0], floats[1])
                    4 -> EdgeInsets(floats[0], floats[1], floats[2], floats[3])
                    else -> null
                }
            }
            else -> null
        }

        fun decode(node: Node.Mapping?): LiquidKeyboard {
            val keyBarNode = node?.get("fixed_key_bar")?.mapping
            val keyBar = keyBarNode?.let {
                val position = keyBarNode["position"]?.enum<KeyBar.Position>()
                    ?: KeyBar.Position.BOTTOM
                val keys = keyBarNode["keys"]?.sequence
                    ?.mapNotNull { item ->
                        when (item) {
                            is Node.Scalar -> {
                                // 字符串格式：直接作为 click
                                FixedKeyItem(click = item.string)
                            }
                            is Node.Mapping -> {
                                // 键值对格式：解析所有属性
                                val click = item["click"]?.string ?: ""
                                val label = item["label"]?.string ?: ""
                                val width = item["width"]?.string?.toFloatOrNull()
                                val height = item["height"]?.string?.toFloatOrNull()
                                // 解析 margin：支持单个值或数组 [left, top, right, bottom]
                                val margin = parseEdgeInsets(item["margin"])
                                // 解析 padding：支持单个值或数组 [left, top, right, bottom]
                                val padding = parseEdgeInsets(item["padding"])
                                FixedKeyItem(
                                    click = click,
                                    label = label,
                                    width = width,
                                    height = height,
                                    margin = margin,
                                    padding = padding,
                                )
                            }
                            else -> {
                                // 其他类型：创建空对象
                                FixedKeyItem()
                            }
                        }
                    } ?: emptyList()
                KeyBar(position = position, keys = keys)
            } ?: KeyBar(emptyList(), KeyBar.Position.BOTTOM)
            val keyboards =
                node?.get("keyboards")?.sequence?.asSequence()
                    ?.mapNotNull { it.string }
                    ?.mapNotNull decode@{ id ->
                        try {
                            val keyboardNode = node[id]?.mapping
                            val type = keyboardNode?.get("type")?.enum<LiquidData.Type>()
                                ?: return@decode null
                            val name = keyboardNode["name"]?.string ?: id
                            val keysNode = keyboardNode["keys"]
                            val keys = arrayListOf<KeyItem>()
                            if (keysNode is Node.Sequence) {
                                keysNode.forEach { item ->
                                    if (item is Node.Mapping) {
                                        val map =
                                            item.entries.associate {
                                                it.key.string!! to it.value.string!!
                                            }
                                        if (map.containsKey("click")) {
                                            val clickText = map["click"] ?: ""
                                            val labelText = map["label"] ?: ""
                                            keys.add(KeyItem(clickText, labelText))
                                        } else {
                                            // [键: 值] 格式：text=值（提交），altText=键（显示）
                                            map.forEach { keys.add(KeyItem(it.value, it.key)) }
                                        }
                                    } else if (item is Node.Scalar) {
                                        keys.add(KeyItem(item.string))
                                    }
                                }
                            } else {
                                val value = keysNode?.string ?: ""
                                if (type == LiquidData.Type.SINGLE) { // single data
                                    value.splitWithSurrogates().forEach {
                                        keys.add(KeyItem(it))
                                    }
                                } else { // simple keyboard data
                                    value
                                        .split("\n+".toRegex())
                                        .filter { it.isNotEmpty() }
                                        .forEach { keys.add(KeyItem(it)) }
                                }
                            }
                            return@decode Keyboard(
                                id = id,
                                type = type,
                                name = name,
                                keys = keys,
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to decode LiquidKeyboard property 'keyboards'")
                            return@decode null
                        }
                    }?.toList() ?: emptyList()
            return LiquidKeyboard(
                singleWidth = node?.get("single_width")?.int ?: 0,
                keyHeight = node?.get("key_height")?.int ?: 0,
                marginX = node?.get("margin_x")?.float ?: 0f,
                fixedKeyBar = keyBar,
                keyboards = keyboards,
            )
        }
    }
}
