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
data class CandidatesTool(
    val navWidth: Int = 44,
    val popupWidth: Int = 0,
    val popupTextSize: Float = 0f,
    val popupTextColor: String = "",
    val popupBackgroundColor: String = "",
    val popupFont: List<String> = emptyList(),
    val background: String = "",
    val separatorColor: String = "",
    val buttonFont: List<String> = emptyList(),
    val buttons: List<ToolBar.Button> = emptyList(),
    val popup: List<PopupAction> = emptyList(),
    val popupByType: Map<String, TypePopupConfig> = emptyMap(),
) : Parcelable {

    @Parcelize
    @Serializable
    data class TypePopupConfig(
        val popupWidth: Int = 0,
        val popup: List<PopupAction> = emptyList(),
    ) : Parcelable

    @Parcelize
    @Serializable
    data class PopupAction(
        val action: String = "",
        val label: String = "",
    ) : Parcelable
}
