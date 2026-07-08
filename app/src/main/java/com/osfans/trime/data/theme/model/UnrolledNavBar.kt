/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import kotlinx.parcelize.Parcelize

@Parcelize
data class UnrolledNavBar(
    val width: Int = 44,
    val background: String = "",
    val separatorColor: String = "",
    val buttonFont: List<String> = emptyList(),
    val buttons: List<ToolBar.Button> = emptyList(),
) : Parcelable {
    companion object {
        fun decode(node: Node.Mapping?): UnrolledNavBar? {
            if (node == null || node["buttons"]?.sequence.isNullOrEmpty()) return null
            return UnrolledNavBar(
                width = node["width"]?.int ?: 44,
                background = node["background"]?.string ?: "",
                separatorColor = node["separator_color"]?.string ?: "",
                buttonFont = node["button_font"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
                buttons = node["buttons"]?.sequence?.map { ToolBar.Button.decode(it.mapping!!) } ?: emptyList(),
            )
        }
    }
}
