/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.osfans.trime.R
import com.osfans.trime.ui.main.CollectionAddActivity
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.imageDrawable

fun ClipboardLayout.setupCollectionFab(
    viewPager: androidx.viewpager2.widget.ViewPager2,
    context: Context,
) {
    val fab = imageView {
        scaleType = ImageView.ScaleType.CENTER
        imageDrawable = drawable(R.drawable.ic_baseline_add_24)!!.apply { setTint(0xFFFFFFFF.toInt()) }
        layoutParams = android.view.ViewGroup.MarginLayoutParams(dp(32), dp(32))
        val backgroundColor = ContextCompat.getColor(context, R.color.blue_500)
        val pressedColor = ColorUtils.blendARGB(backgroundColor, 0xFF000000.toInt(), 0.12f)
        val radius = dp(16).toFloat()
        background = StateListDrawable().apply {
            fun createDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius
            }
            addState(intArrayOf(android.R.attr.state_pressed), createDrawable(pressedColor))
            addState(intArrayOf(), createDrawable(backgroundColor))
        }
        elevation = dp(6).toFloat()
        isClickable = true
        isFocusable = true
    }

    add(
        fab,
        lParams(dp(32), dp(32)) {
            bottomOfParent(dp(12))
            endOfParent(dp(12))
        },
    )

    fab.setOnClickListener {
        context.startActivity(
            Intent(context, CollectionAddActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    viewPager.registerOnPageChangeCallback(
        object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                fab.isVisible = position == 1
            }
        },
    )

    fab.isVisible = viewPager.currentItem == 1
}
