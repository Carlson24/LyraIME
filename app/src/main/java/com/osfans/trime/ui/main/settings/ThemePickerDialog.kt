// SPDX-FileCopyrightText: 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.common.pickSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemePickerDialog {
    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val allThemes =
            withContext(Dispatchers.IO) {
                ThemeManager.getAllThemes()
            }
        val selectedTheme by ThemeManager.prefs.selectedTheme
        val selectedIndex = allThemes.indexOfFirst { it.configId == selectedTheme }
        return context.pickSingle(
            scope = scope,
            title = R.string.selected_theme,
            items = allThemes.map { it.name }.toTypedArray(),
            selectedIndex = selectedIndex,
            emptyMessage = R.string.no_theme_to_select,
            onSelect = { index ->
                afterConfirm?.invoke()
                ThemeManager.selectTheme(allThemes[index].configId)
            },
        )
    }
}
