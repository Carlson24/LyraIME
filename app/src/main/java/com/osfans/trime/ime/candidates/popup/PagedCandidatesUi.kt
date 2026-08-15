/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.MenuProto
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.composition.CandidatesView
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.roundToInt

class PagedCandidatesUi(
    override val ctx: Context,
    val theme: Theme,
    private val onCandidateClick: (Int) -> Unit,
    private val onCandidateAction: (Int, String, String, View) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit,
) : Ui {
    private var menu = MenuProto()

    private var isHorizontal = true

    private var isReversed = false

    /**
     * Maximum width in pixels of a candidate item.
     * Only limited in vertical layout, so that a long candidate wraps
     * to multiple lines instead of making the window wider than the screen.
     */
    private var maxItemWidth = Int.MAX_VALUE

    sealed class UiHolder(
        open val ui: Ui,
    ) : RecyclerView.ViewHolder(ui.root) {
        class Candidate(
            override val ui: LabeledCandidateItemUi,
        ) : UiHolder(ui)

        class Pagination(
            override val ui: PaginationUi,
        ) : UiHolder(ui)
    }

    private val candidatesAdapter =
        object : BaseQuickAdapter<CandidateProto, UiHolder>() {
            init {
                // We must do this to avoid ArrayIndexOutOfBoundsException
                // https://github.com/google/flexbox-layout/issues/363#issuecomment-382949953
                setHasStableIds(true)
            }

            override fun getItemId(position: Int): Long = items.getOrNull(position).hashCode().toLong()

            override fun getItemCount(items: List<CandidateProto>) = items.size + (if (menu.pageNumber != 0 || !menu.isLastPage) 1 else 0)

            override fun getItemViewType(
                position: Int,
                list: List<CandidateProto>,
            ) = if (position < list.size) 0 else 1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ): UiHolder = when (viewType) {
                0 -> UiHolder.Candidate(LabeledCandidateItemUi(ctx, theme))

                else ->
                    UiHolder.Pagination(PaginationUi(ctx, theme)).apply {
                        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
                        ui.root.layoutParams =
                            FlexboxLayoutManager.LayoutParams(wrap, wrap).apply {
                                flexGrow = 1f
                            }
                    }
            }

            override fun onBindViewHolder(
                holder: UiHolder,
                position: Int,
                item: CandidateProto?,
            ) {
                when (holder) {
                    is UiHolder.Candidate -> {
                        val candidate = item ?: return
                        holder.ui.root.maxWidth = maxItemWidth
                        holder.ui.update(candidate, position == menu.highlightedCandidateIndex)
                        holder.ui.root.setOnClickListener {
                            onCandidateClick.invoke(position)
                        }
                        holder.ui.root.setOnLongClickListener { v ->
                            onCandidateAction.invoke(position, candidate.text, candidate.type, v)
                            true
                        }
                    }

                    is UiHolder.Pagination -> {
                        holder.ui.update(menu)
                        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                            width = if (isHorizontal) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
                            alignSelf = if (isHorizontal) AlignItems.CENTER else AlignItems.STRETCH
                        }
                        holder.ui.prevIcon.setOnClickListener {
                            onPrevPage.invoke()
                        }
                        holder.ui.nextIcon.setOnClickListener {
                            onNextPage.invoke()
                        }
                    }
                }
            }
        }

    private val candidatesLayoutManager =
        FlexboxLayoutManager(ctx).apply {
            flexWrap = FlexWrap.WRAP
        }

    override val root =
        recyclerView {
            itemAnimator = null
            isFocusable = false
            adapter = candidatesAdapter
            layoutManager = candidatesLayoutManager
            overScrollMode = View.OVER_SCROLL_NEVER
        }

    fun update(
        menu: MenuProto,
        horizontal: Boolean,
        layout: PopupCandidatesLayout,
        reversed: Boolean,
    ) {
        this.menu = menu
        this.isHorizontal = when (layout) {
            PopupCandidatesLayout.AUTOMATIC -> horizontal
            else -> layout == PopupCandidatesLayout.HORIZONTAL
        }
        this.isReversed = reversed && !isHorizontal
        maxItemWidth = if (isHorizontal) {
            Int.MAX_VALUE
        } else {
            // window's horizontal margins: spacing to the screen edges + window insets
            val margins = 2 * (ctx.dp(CandidatesView.SPACING) + ctx.dp(theme.window.insets.horizontal))
            (ctx.resources.displayMetrics.widthPixels - margins).roundToInt().coerceAtLeast(0)
        }
        candidatesLayoutManager.apply {
            when {
                isHorizontal -> {
                    flexDirection = FlexDirection.ROW
                    alignItems = AlignItems.BASELINE
                }

                isReversed -> {
                    flexDirection = FlexDirection.COLUMN_REVERSE
                    alignItems = AlignItems.STRETCH
                }

                else -> {
                    flexDirection = FlexDirection.COLUMN
                    alignItems = AlignItems.STRETCH
                }
            }
        }
        candidatesAdapter.submitList(menu.candidates.toList())
    }
}
