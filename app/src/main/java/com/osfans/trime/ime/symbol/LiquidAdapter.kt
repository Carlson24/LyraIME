/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.AutoScaleTextView
import splitties.dimensions.dp
import splitties.views.gravityCenter

class LiquidAdapter(
    private val theme: Theme,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : BaseQuickAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>() {
    companion object {
        private const val VIEW_TYPE_FIXED = 0
        private const val VIEW_TYPE_VAR_LENGTH = 1
        private const val VIEW_TYPE_PLACEHOLDER = 2
    }

    var currentDataType: LiquidData.Type = LiquidData.Type.SINGLE

    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun getItemViewType(position: Int, list: List<LiquidKeyboard.KeyItem>): Int {
        val item = list.getOrNull(position)
        return if (item != null && (item.text.isNotEmpty() || item.altText.isNotEmpty())) {
            if (LiquidData.isVarLengthType(currentDataType)) {
                VIEW_TYPE_VAR_LENGTH
            } else {
                VIEW_TYPE_FIXED
            }
        } else {
            VIEW_TYPE_PLACEHOLDER
        }
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val ui = LiquidItemUi(context, theme)
        ui.mainText.apply {
            scaleMode = AutoScaleTextView.Mode.Proportional
            gravity = gravityCenter
        }
        return ViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: LiquidKeyboard.KeyItem?,
    ) {
        item ?: return

        val context = holder.ui.root.context
        val viewType = getItemViewType(position)

        when (viewType) {
            VIEW_TYPE_PLACEHOLDER -> {
                holder.ui.root.apply {
                    visibility = View.INVISIBLE
                    layoutParams = FlexboxLayoutManager.LayoutParams(
                        context.dp(theme.liquidKeyboard.singleWidth),
                        1,
                    ).apply {
                        flexGrow = 1f
                    }
                }
            }

            VIEW_TYPE_VAR_LENGTH -> {
                holder.ui.root.apply {
                    visibility = View.VISIBLE
                    layoutParams = FlexboxLayoutManager.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        context.dp(theme.liquidKeyboard.keyHeight),
                    ).apply {
                        flexGrow = 1f
                        minWidth = if (currentDataType == LiquidData.Type.HISTORY) {
                            context.dp(theme.liquidKeyboard.singleWidth)
                        } else {
                            0
                        }
                    }
                    forceLayout()
                }
            }

            VIEW_TYPE_FIXED -> {
                holder.ui.root.apply {
                    visibility = View.VISIBLE
                    layoutParams = FlexboxLayoutManager.LayoutParams(
                        context.dp(theme.liquidKeyboard.singleWidth),
                        context.dp(theme.liquidKeyboard.keyHeight),
                    ).apply {
                        flexGrow = 0f
                        flexShrink = 0f
                    }
                }
            }
        }

        if (viewType != VIEW_TYPE_PLACEHOLDER) {
            holder.ui.mainText.text =
                // 优先显示 altText（label/键），如果为空则显示 text（click/值）
                if (item.altText.isNotEmpty()) item.altText else item.text
            holder.ui.root.setOnClickListener {
                onItemClick.invoke(item, position)
            }
        }
    }
}
