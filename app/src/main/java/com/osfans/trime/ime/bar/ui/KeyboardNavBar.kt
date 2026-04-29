/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add

class KeyboardNavBar(
    private val context: Context,
    private val theme: Theme,
    private val tabUi: TabUi,
    private val onAttach: () -> Unit,
    private val onDetach: () -> Unit,
) {
    private val size = context.dp(theme.generalStyle.run { candidateViewHeight + commentHeight })

    private var currentNavBarView: View? = null

    fun attach(
        title: String,
        onCloseClick: () -> Unit,
        onBackClick: (() -> Unit)? = null,
    ) {
        tabUi.removeExternal()

        tabUi.setTitle(title)
        val navBarView = createNavBarView(onCloseClick)
        currentNavBarView = navBarView
        tabUi.addExternal(navBarView, true)
        tabUi.setBackButtonOnClickListener {
            onBackClick?.invoke()
        }
        onAttach()
    }

    fun detach() {
        if (currentNavBarView == null) return
        tabUi.removeExternal()
        currentNavBarView = null
        onDetach()
    }

    private fun createNavBarView(onCloseClick: () -> Unit): View = context.constraintLayout {
        val closeButton = createCloseButton(onCloseClick)
        add(
            closeButton,
            lParams(size, size) {
                endOfParent()
                centerVertically()
            },
        )
    }

    private fun createCloseButton(onClick: () -> Unit): ToolButton {
        val buttonConfig = ToolBar.Button(
            foreground = ToolBar.Button.Foreground(style = "ic@keyboard_close"),
        )
        return ToolButton(context, buttonConfig).apply {
            setOnClickListener { onClick() }
        }
    }
}
