/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.window

import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxHorizontalDecoration
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.window.BoardWindow
import splitties.dimensions.dp
import splitties.views.dsl.core.wrapContent

class FlexboxUnrolledCandidateWindow : BaseUnrolledCandidateWindow() {
    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply {
        slideEdge = Gravity.TOP
    }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(theme) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                itemView.apply {
                    minimumWidth = dp(40)
                    val itemHeight = dp(theme.generalStyle.run { candidateViewHeight + commentHeight })
                    layoutParams =
                        FlexboxLayoutManager
                            .LayoutParams(wrapContent, itemHeight)
                            .apply { flexGrow = 1f }
                }
            }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.FLEX_START
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, theme).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
            addItemDecoration(FlexboxHorizontalDecoration(separatorDrawable))
        }
        navBar?.setOnActionClick { actionString, view ->
            InputFeedbackManager.keyPressVibrate(view)
            val keyAction = KeyActionManager.getAction(actionString)
            val rimeKeyVal = RimeKeyMapping
                .keyCodeToVal(keyAction.code)
                .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
                ?: RimeKeyEvent.getKeycodeByName(KeyCode.codeToKeyName(keyAction.code) ?: "VoidSymbol")
            val rimeMods = KeyModifiers.fromMetaState(keyAction.modifier)
            rime.launchOnReady { rimeApi ->
                rimeApi.processKey(rimeKeyVal, rimeMods.modifiers)
            }
        }
    }
}
