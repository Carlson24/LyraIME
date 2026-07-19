/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.os.Parcelable
import com.osfans.trime.data.theme.model.CandidatesTool
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/** 主题和样式配置  */
@Parcelize
@Serializable
data class Theme(
    val name: String,
    val version: String = "",
    val author: String = "",
    @SerialName("style")
    val generalStyle: GeneralStyle,
    val preedit: Preedit = Preedit(),
    val window: Window = Window(),
    val liquidKeyboard: LiquidKeyboard = LiquidKeyboard(),
    val presetKeys: Map<String, PresetKey> = emptyMap(),
    val presetKeyboards: Map<String, TextKeyboard> = emptyMap(),
    @SerialName("preset_color_schemes")
    val colorSchemes: List<ColorScheme> = emptyList(),
    val fallbackColors: Map<String, String> = emptyMap(),
    val toolBar: ToolBar = ToolBar(),
    val candidatesTool: CandidatesTool? = null,
) : Parcelable {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
