/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.databinding.ActivityClipEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.inputMethodManager

class CollectionAddActivity : Activity() {
    private val scope: CoroutineScope = MainScope()
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding =
            ActivityClipEditBinding.inflate(layoutInflater).apply {
                editText = clipEditText
                clipEditCancel.setOnClickListener { finish() }
                clipEditOk.setOnClickListener { finishAdding() }
            }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, 0)
    }

    private fun finishAdding() {
        val str = editText.editableText.toString().trim()
        if (str.isNotEmpty()) {
            scope.launch {
                withContext(NonCancellable) {
                    CollectionHelper.addNewBean(str)
                }
            }
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
