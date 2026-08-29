// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class LiquidGridDecoration(
    private val spanCount: Int,
    private val dividerSize: Int,
    dividerColor: Int,
) : RecyclerView.ItemDecoration() {
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        color = dividerColor
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val itemCount = parent.adapter?.itemCount ?: return
        val isLastColumn = (position + 1) % spanCount == 0
        val isLastRow = position / spanCount == (itemCount - 1) / spanCount
        outRect.right = if (isLastColumn) 0 else dividerSize
        outRect.bottom = if (isLastRow) 0 else dividerSize
    }

    override fun onDraw(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val childCount = parent.childCount
        val itemCount = parent.adapter?.itemCount ?: return
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val hasRightOffset = (position + 1) % spanCount != 0
            val hasBottomOffset = position / spanCount != (itemCount - 1) / spanCount

            // 竖分割线：占右侧预留间隙，向下延伸与下一行的竖线相接
            if (hasRightOffset) {
                val bottom = child.bottom + if (hasBottomOffset) dividerSize else 0
                c.drawRect(
                    child.right.toFloat(),
                    child.top.toFloat(),
                    (child.right + dividerSize).toFloat(),
                    bottom.toFloat(),
                    paint,
                )
            }
            // 横分割线：占底部预留间隙，向右延伸与下一列的横线相接
            if (hasBottomOffset) {
                val right = child.right + if (hasRightOffset) dividerSize else 0
                c.drawRect(
                    child.left.toFloat(),
                    child.bottom.toFloat(),
                    right.toFloat(),
                    (child.bottom + dividerSize).toFloat(),
                    paint,
                )
            }
        }
    }
}
