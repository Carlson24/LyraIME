/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.db

import androidx.annotation.StringRes
import com.osfans.trime.R

enum class ClipboardCategory(@field:StringRes val titleRes: Int) {
    All(R.string.clipboard_category_all),
    Favorites(R.string.clipboard_category_favorites),
    Local(R.string.clipboard_category_local),
    Media(R.string.clipboard_category_media),
    Remote(R.string.clipboard_category_remote),
}
