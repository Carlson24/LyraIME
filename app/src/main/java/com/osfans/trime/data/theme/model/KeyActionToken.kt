/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class KeyActionToken : Parcelable {
    data class Plain(val token: String) : KeyActionToken()
    data class Inline(val token: Token) : KeyActionToken() {
        @Parcelize
        data class Token(
            val commit: String?,
            val text: String?,
            val label: String?,
        ) : Parcelable
    }
}
