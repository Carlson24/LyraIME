/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.extractAll
import kotlin.text.Regex

class ClipboardExtractor(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val windowManager: BoardWindowManager,
) {
    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste

    val extractRules: Set<Regex> by lazy {
        val rules by prefs.clipboardExtractRules
        rules
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull {
                try {
                    Regex(it)
                } catch (e: Exception) {
                    null
                }
            }
            .toSet()
    }

    fun showExtractDialog(text: String) {
        val extracted = text.extractAll(extractRules)
        if (extracted.isEmpty()) {
            val dialog = AlertDialog.Builder(context)
                .setMessage(R.string.no_extraction_results)
                .setPositiveButton(R.string.ok, null)
                .create()
            service.showDialog(dialog)
            return
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.select_extracted_content)
            .setItems(extracted.toTypedArray()) { _, which ->
                service.commitText(extracted[which])
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }
}
