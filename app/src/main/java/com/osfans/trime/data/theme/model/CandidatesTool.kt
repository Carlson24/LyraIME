/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.parcelize.Parcelize

@Parcelize
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
) : Parcelable {

    @Parcelize
    data class PopupAction(
        val action: String = "",
        val label: String = "",
    ) : Parcelable {
        companion object {
            fun decode(node: Node.Mapping): PopupAction = PopupAction(
                action = node["action"]?.string ?: "",
                label = node["label"]?.string ?: "",
            )
        }
    }

    companion object {
        fun decode(node: Node.Mapping?): CandidatesTool? {
            if (node == null) return null
            if (node["buttons"]?.sequence.isNullOrEmpty() &&
                node["popup"]?.sequence.isNullOrEmpty() &&
                (node["popup_width"]?.int ?: 0) <= 0
            ) {
                return null
            }
            return CandidatesTool(
                navWidth = node["nav_width"]?.int ?: 44,
                popupWidth = node["popup_width"]?.int ?: 0,
                popupTextSize = node["popup_text_size"]?.float ?: 0f,
                popupTextColor = node["popup_text_color"]?.string ?: "",
                popupBackgroundColor = node["popup_background_color"]?.string ?: "",
                popupFont = node["popup_font"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
                background = node["background"]?.string ?: "",
                separatorColor = node["separator_color"]?.string ?: "",
                buttonFont = node["button_font"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
                buttons = node["buttons"]?.sequence?.map { ToolBar.Button.decode(it.mapping!!) } ?: emptyList(),
                popup = node["popup"]?.sequence?.map { PopupAction.decode(it.mapping!!) } ?: emptyList(),
            )
        }
    }
}
