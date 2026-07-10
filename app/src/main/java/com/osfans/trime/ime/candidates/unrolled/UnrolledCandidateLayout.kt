// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.unrolled.nav.CandidatesToolView
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    theme: Theme,
) : ConstraintLayout(context) {
    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    val navBar: CandidatesToolView? =
        if (theme.candidatesTool != null) {
            CandidatesToolView(context, theme)
        } else {
            null
        }

    init {
        id = R.id.unrolled_candidate_view
        background =
            ColorManager.getDecorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.generalStyle.candidateBorder),
                dp(theme.generalStyle.candidateBorderRound),
            )

        if (navBar != null) {
            val navWidth = dp(theme.candidatesTool!!.width)
            add(
                navBar,
                lParams(navWidth, matchParent) {
                    endOfParent()
                    topOfParent()
                },
            )
            add(
                recyclerView,
                lParams(matchParent, matchParent) {
                    startOfParent()
                    endToStartOf(navBar)
                    topOfParent()
                },
            )
        } else {
            add(
                recyclerView,
                lParams(matchParent, matchParent) {
                    startOfParent()
                    endOfParent()
                    topOfParent()
                },
            )
        }
    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }
}
