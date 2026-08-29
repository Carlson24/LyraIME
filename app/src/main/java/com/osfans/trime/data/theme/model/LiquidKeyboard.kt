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
