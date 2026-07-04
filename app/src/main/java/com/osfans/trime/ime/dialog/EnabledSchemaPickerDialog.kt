// SPDX-FileCopyrightText: 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.data.packaging.SchemaPackageManager
import com.osfans.trime.ui.common.buildDialog
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager
import timber.log.Timber

object EnabledSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val allSchemas = SchemaPackageManager.getAllSchemas()
        Timber.d("SchemaPicker: allSchemas size=${allSchemas.size}")
        val names = allSchemas.map { (schema, pkg) ->
            if (allSchemas.count { it.first.id == schema.id } > 1) {
                "${schema.name.ifEmpty { schema.id }} (${pkg.id})"
            } else {
                schema.name.ifEmpty { schema.id }
            }
        }.toTypedArray()
        val ids = allSchemas.map { (schema, _) -> schema.id }
        val selectedSchemaId = rime.selectedSchemaId()
        val currentIndex = ids.indexOf(selectedSchemaId).coerceAtLeast(0)
        var selectedIndex = currentIndex

        return context.buildDialog(R.string.select_current_schema)
            .apply {
                if (names.isEmpty()) {
                    setMessage(R.string.no_schema_to_select)
                } else {
                    setSingleChoiceItems(names, selectedIndex) { _, which ->
                        selectedIndex = which
                    }
                }
            }
            .setPositiveButton(R.string.enable_schemata) { _, _ ->
                if (selectedIndex < 0 || selectedIndex >= ids.size) return@setPositiveButton
                val schemaId = ids[selectedIndex]
                scope.launch { rime.selectSchemaCrossPackage(schemaId) }
            }
            .setNeutralButton(R.string.other_ime) { _, _ ->
                inputMethodManager.showInputMethodPicker()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
