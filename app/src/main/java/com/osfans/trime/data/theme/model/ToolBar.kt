/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    val buttonFont: List<String> = emptyList(),
    val backStyle: String = "ic@arrow-left",
) : Parcelable {

    @Parcelize
    @Serializable
    data class Button(
        val background: Background = Background(),
        val foreground: Foreground = Foreground(),
        val action: String = "",
        val longPressAction: String = "",
        val size: List<Int> = emptyList(),
    ) : Parcelable {

        @Parcelize
        @Serializable
        data class Background(
            val type: String = "rectangle",
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) : Parcelable

        @Parcelize
        @Serializable
        data class Foreground(
            val style: String = "",
            val optionStyles: List<String> = emptyList(),
            val normal: String = "",
            val highlight: String = "",
            val fontSize: Float = 18f,
            val padding: Int = 4,
        ) : Parcelable
    }
}
