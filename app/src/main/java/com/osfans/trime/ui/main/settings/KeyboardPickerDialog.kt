/*
 * SPDX-FileCopyrightText: 2025 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.common.pickSingle

object KeyboardPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
        onSelectKeyboard: (String) -> Unit = {},
    ): AlertDialog {
        val visibleIds = linkedSetOf<String>()
        for (id in ThemeManager.keyboardNames) {
            val kb = ThemeManager.getKeyboard(id) ?: continue
            if (kb.dynamicMode) {
                if (kb.dynamicOriginal.isNotEmpty()) {
                    visibleIds.add(kb.dynamicOriginal)
                }
            } else {
                visibleIds.add(id)
            }
        }
        val ids = visibleIds.toList()
        val items = ids.map { ThemeManager.getKeyboard(it)?.name ?: it }.toTypedArray()
        return context.pickSingle(
            scope = scope,
            title = R.string.keyboard_layout,
            items = items,
            selectedIndex = -1,
            emptyMessage = R.string.no_keyboard_to_select,
            onSelect = { index ->
                afterConfirm?.invoke()
                onSelectKeyboard(ids[index])
            },
        )
    }
}
