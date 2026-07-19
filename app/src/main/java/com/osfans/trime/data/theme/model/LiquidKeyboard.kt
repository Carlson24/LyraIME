/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.symbol.LiquidData
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class LiquidKeyboard(
    val singleWidth: Int = 0,
    val keyHeight: Int = 0,
    val marginX: Float = 0f,
    val fixedKeyBar: KeyBar = KeyBar(),
    val keyboards: List<Keyboard> = emptyList(),
) : Parcelable {
    @Parcelize
    @Serializable
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
    @Serializable
    data class KeyBar(
        val keys: List<FixedKeyItem> = emptyList(),
        val position: Position = Position.BOTTOM,
    ) : Parcelable {
        @Serializable
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
            NAVBAR,
        }
    }

    @Parcelize
    @Serializable
    data class FixedKeyItem(
        val click: String = "",
        val label: String = "",
        val width: Float? = null,
        val height: Float? = null,
        val margin: EdgeInsets? = null,
        val padding: EdgeInsets? = null,
        val isStringFormat: Boolean = false,
    ) : Parcelable

    @Parcelize
    @Serializable
    data class Keyboard(
        val id: String = "",
        val type: LiquidData.Type,
        val name: String = "",
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
