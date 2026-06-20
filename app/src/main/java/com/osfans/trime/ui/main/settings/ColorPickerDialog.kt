// SPDX-FileCopyrightText: 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.common.pickSingle

object ColorPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val presetSchemes = ThemeManager.activeTheme.colorSchemes
        val currentScheme = ColorManager.activeColorScheme
        val currentIndex = presetSchemes.indexOfFirst { it.id == currentScheme.id }
        return context.pickSingle(
            scope = scope,
            title = R.string.normal_mode_color,
            items = presetSchemes.map { it.colors["name"] ?: "" }.toTypedArray(),
            selectedIndex = currentIndex,
            emptyMessage = R.string.no_color_to_select,
            onSelect = { index ->
                afterConfirm?.invoke()
                if (index != currentIndex) {
                    ColorManager.setColorScheme(presetSchemes[index])
                }
            },
        )
    }
}
