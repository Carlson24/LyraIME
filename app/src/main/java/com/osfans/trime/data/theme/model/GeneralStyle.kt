/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class GeneralStyle(
    val fonts: FontStyle = FontStyle(),
    val autoCaps: Boolean = false,
    val candidateBorder: Int = 0,
    val candidateBorderRound: Float = 0f,
    val candidatePadding: Int = 0,
    val candidateSpacing: Float = 0f,
    val candidateTextVerticalBias: Float = 1f,
    val candidateViewHeight: Int = 28,
    val candidateCornerRadius: Float = 5f,
    val commentHeight: Int = 12,
    val commentPosition: CommentPosition = CommentPosition.RIGHT,
    val commentVerticalBias: Float = 0f,
    val horizontalGap: Int = 0,
    val keyboardPadding: Int = 0,
    val keyboardPaddingBottom: Int = 0,
    val keyboardPaddingLand: Int = 0,
    val keyboardPaddingLandBottom: Int = 0,
    val keyBorder: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val popupBottomMargin: Int = 0,
    val popupWidth: Int = 0,
    val popupHeight: Int = 0,
    val popupKeyHeight: Int = 0,
    val resetAsciiModeOnFocusChange: Boolean = false,
    val roundCorner: Float = 0f,
    val shadowRadius: Float = 0f,
    val verticalGap: Int = 0,
    val backgroundFolder: String = "backgrounds",
    val enterLabelMode: Int = 0,
    @SerialName("enter_labels")
    val enterLabel: EnterLabel = EnterLabel(),
    val keyboardPaddingTop: Int = 0,
    val t9SideRoundCorner: Float = -1f,
) : Parcelable {
    @Serializable
    enum class CommentPosition {
        RIGHT,
        TOP,
        OVERLAY,
    }

    @Parcelize
    @Serializable
    data class FontStyle(
        val candidate: List<String> = emptyList(),
        val comment: List<String> = emptyList(),
        val key: List<String> = emptyList(),
        val label: List<String> = emptyList(),
        val latin: List<String> = emptyList(),
        val symbol: List<String> = emptyList(),
        val text: List<String> = emptyList(),
        val hint: List<String> = emptyList(),
        val hanb: List<String> = emptyList(),
        val popup: List<String> = emptyList(),
        val t9_side: List<String> = emptyList(),
        val clipboard: List<String> = emptyList(),
        val clipboard_category: List<String> = emptyList(),
        val candidate_size: Float = 15f,
        val comment_size: Float = 10f,
        val key_size: Float = 15f,
        val key_long_size: Float = 15f,
        val label_size: Float = 0f,
        val symbol_size: Float = 0f,
        val hint_size: Float = 0f,
        val popup_size: Float = 0f,
        val clipboard_category_size: Float = 13f,
        val clipboard_size: Float = 14f,
        val t9_side_size: Float = -1f,
        val variations: Map<String, Boolean> = emptyMap(),
        val display: Map<String, String> = emptyMap(),
    ) : Parcelable

    @Parcelize
    @Serializable
    data class EnterLabel(
        val go: String = "go",
        val done: String = "done",
        val next: String = "next",
        val pre: String = "pre",
        val search: String = "search",
        val send: String = "send",
        val default: String = "default",
    ) : Parcelable
}
