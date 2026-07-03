/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.clipboard

import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.button
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

sealed class ClipboardInstructionUi(override val ctx: Context) : Ui {

    class Enable(ctx: Context) : ClipboardInstructionUi(ctx) {

        private val instructionText = textView {
            setText(R.string.clipboard_listening_enable_hint)
            setPaddingDp(12, 8, 12, 8)
            setTextColor(ColorManager.getColor("key_text_color"))
        }

        val enableButton = button {
            setText(R.string.clipboard_listening_enable_title)
            setTextColor(ColorManager.getColor("key_text_color"))
        }

        override val root = constraintLayout {
            add(
                instructionText,
                lParams(wrapContent, wrapContent) {
                    topOfParent()
                    startOfParent()
                    endOfParent()
                },
            )
            add(
                enableButton,
                lParams(wrapContent, wrapContent) {
                    below(instructionText)
                    endOfParent(dp(8))
                },
            )
        }
    }

    class Empty(ctx: Context) : ClipboardInstructionUi(ctx) {

        private val instructionText = textView {
            setText(R.string.clipboard_empty_hint)
            setTextColor(ColorManager.getColor("key_text_color"))
        }

        override val root = constraintLayout {
            add(
                instructionText,
                lParams(wrapContent, wrapContent) {
                    topOfParent(dp(24))
                    startOfParent()
                    endOfParent()
                },
            )
        }
    }
}
