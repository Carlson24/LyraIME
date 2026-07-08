/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.nav

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import kotlin.math.max

class UnrolledNavBarView(
    context: Context,
    theme: Theme,
) : LinearLayout(context) {
    private data class ButtonEntry(
        val button: NavBarButton,
        val action: String,
    )

    private val buttonEntries = mutableListOf<ButtonEntry>()

    init {
        val navConfig = theme.unrolledNavBar!!
        orientation = HORIZONTAL

        val separatorColor = ColorManager.getColor("candidate_separator_color")
        val separatorWidth = max(theme.generalStyle.candidateSpacing, dp(theme.generalStyle.candidateSpacing)).toInt()

        val separatorView = View(context).apply {
            setBackgroundColor(separatorColor)
        }
        addView(separatorView, LayoutParams(separatorWidth, LayoutParams.MATCH_PARENT))

        val buttonsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            navConfig.buttons.forEach { buttonConfig ->
                val button = NavBarButton(context, buttonConfig) { }
                buttonEntries.add(ButtonEntry(button, buttonConfig.action))
                addView(button, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
        addView(buttonsContainer, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        if (navConfig.background.isNotEmpty()) {
            setBackgroundColor(ColorManager.getColor(navConfig.background))
        }
    }

    fun setOnActionClick(listener: (String, View) -> Unit) {
        buttonEntries.forEach { (button, action) ->
            button.setOnClickListener { listener(action, it) }
        }
    }
}
