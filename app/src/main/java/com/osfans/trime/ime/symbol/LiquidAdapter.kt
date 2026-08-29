/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.AutoScaleTextView
import splitties.views.gravityCenter

class LiquidAdapter(
    private val theme: Theme,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : BaseQuickAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>() {
    var itemHeightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var activePosition: Int = RecyclerView.NO_POSITION
        set(value) {
            if (field == value) return
            if (field != RecyclerView.NO_POSITION) notifyItemChanged(field)
            field = value
            if (value != RecyclerView.NO_POSITION) notifyItemChanged(value)
        }

    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

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

        holder.ui.root.apply {
            layoutParams = GridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemHeightPx.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            if (item.text.isNotEmpty() || item.altText.isNotEmpty()) {
                setOnClickListener {
                    onItemClick.invoke(item, position)
                }
            } else {
                setOnClickListener(null)
            }
        }
        holder.ui.setActive(position == activePosition)
        holder.ui.mainText.text =
            // 优先显示 altText（label/键），如果为空则显示 text（click/值）
            if (item.altText.isNotEmpty()) item.altText else item.text
    }
}
