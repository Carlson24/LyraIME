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
data class SyncInput(
    val fontSize: Float? = null,
    val alpha: Float? = null,
    val cornerRadius: Float? = null,
    val horizontalPadding: Int? = null,
    val verticalPadding: Int? = null,
    val maxHeight: Int? = null,
    val button: ToolBar.Button? = null,
) : Parcelable
