// SPDX-FileCopyrightText: 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.ui.common.pickSingle
import splitties.systemservices.inputMethodManager

object EnabledSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        scope: LifecycleCoroutineScope,
        context: Context,
        extensions: (AlertDialog.Builder.() -> Unit)? = null,
    ): AlertDialog {
        val selecteds = rime.selectedSchemata()
        val selectedNames = selecteds.map { it.name }
        val selectedIds = selecteds.map { it.id }
        val selectedSchemaId = rime.selectedSchemaId()
        val selectedIndex = selecteds.indexOfFirst { it.id == selectedSchemaId }
        return context.pickSingle(
            scope = scope,
            title = R.string.select_current_schema,
            items = selectedNames.toTypedArray(),
            selectedIndex = selectedIndex,
            emptyMessage = R.string.no_schema_to_select,
            onSelect = { index ->
                rime.selectSchema(selectedIds[index])
            },
            builderBlock = {
                setNeutralButton(R.string.other_ime) { _, _ ->
                    inputMethodManager.showInputMethodPicker()
                }
                extensions?.invoke(this)
            },
        )
    }
}
