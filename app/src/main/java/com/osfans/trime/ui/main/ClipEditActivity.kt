/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.databinding.ActivityClipEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager
import timber.log.Timber

class ClipEditActivity : Activity() {
    private val scope: CoroutineScope = MainScope()
    private var beanId: Int = -1
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding =
            ActivityClipEditBinding.inflate(layoutInflater).apply {
                editText = clipEditText
                clipEditCancel.setOnClickListener { finish() }
                clipEditOk.setOnClickListener { finishEditing() }
            }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, 0)
        processIntent(intent)
    }

    private fun finishEditing() {
        val str = editText.editableText.toString()
        scope.launch {
            ClipboardHelper.updateText(beanId, str)
        }
        finish()
    }

    private fun setBean(bean: DatabaseBean) {
        beanId = bean.id
        editText.setText(bean.text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        scope.launch {
            intent.run {
                val beanId = getIntExtra(BEAN_ID, -1)
                Timber.d("processIntent: id=$beanId")
                ClipboardHelper.get(beanId)?.also {
                    setBean(it)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val BEAN_ID = "id"
    }
}
