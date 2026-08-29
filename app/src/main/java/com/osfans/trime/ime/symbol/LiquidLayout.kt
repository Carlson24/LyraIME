// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class LiquidLayout(
    context: Context,
    private val theme: Theme,
) : LinearLayout(context) {
    private val sideMarginPx = context.dp(
        if (context.isLandscapeMode()) {
            theme.generalStyle.keyboardPaddingLand
        } else {
            theme.generalStyle.keyboardPadding
        },
    )

    private val bottomPadding =
        theme.liquidKeyboard.bottomPadding
            ?: if (context.isLandscapeMode()) {
                theme.generalStyle.keyboardPaddingLandBottom
            } else {
                theme.generalStyle.keyboardPaddingBottom
            }

    private val verticalGapPx = context.dp(theme.generalStyle.verticalGap.coerceAtLeast(0))

    private val contentPaddingPx = context.dp(theme.generalStyle.contentPadding.coerceAtLeast(0))

    val gridDividerSize = context.dp(1)

    var onGridMeasured: ((width: Int, height: Int) -> Unit)? = null

    val tabsUi = LiquidTabsUi(context, theme)

    val lockIcon = createIcon()

    val returnIcon = createIcon()

    val lockButton = createIconButton(lockIcon)

    val returnButton = createIconButton(returnIcon)

    val recyclerView =
        recyclerView {
            setPadding(0, contentPaddingPx, 0, contentPaddingPx)
            addItemDecoration(
                LiquidGridDecoration(
                    spanCount = theme.liquidKeyboard.columns,
                    dividerSize = gridDividerSize,
                    dividerColor = ColorManager.getColor("liquid_keyboard_divider_color"),
                ),
            )
        }

    init {
        orientation = HORIZONTAL
        setPadding(sideMarginPx, 0, sideMarginPx, context.dp(bottomPadding.coerceAtLeast(0)))

        // 左侧栏：导航(3/5) + 锁定(1/5) + 返回(1/5)，占宽 1/5
        val leftPanel = view(::LinearLayout) {
            orientation = VERTICAL
            tabsUi.root.background = ColorManager.getDecorDrawable(
                "key_back_color",
                "key_border_color",
                dp(theme.generalStyle.keyBorder),
                dp(theme.generalStyle.roundCorner),
            )
            tabsUi.root.setPadding(0, contentPaddingPx, 0, contentPaddingPx)
            tabsUi.root.clipToOutline = true
            tabsUi.root.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radius = context.dp(theme.generalStyle.roundCorner)
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            add(
                tabsUi.root,
                lParams(matchParent, 0, weight = 3f),
            )
            add(
                lockButton,
                lParams(matchParent, 0, weight = 1f) { topMargin = verticalGapPx },
            )
            add(
                returnButton,
                lParams(matchParent, 0, weight = 1f) { topMargin = verticalGapPx },
            )
        }
        add(leftPanel, lParams(0, matchParent, weight = 1f))

        // 右侧符号面板，占宽 4/5，整体渲染单一背景
        val rightPanel = view(::FrameLayout) {
            background = ColorManager.getDecorDrawable(
                "liquid_keyboard_background",
                "liquid_keyboard_board",
                dp(theme.generalStyle.keyBorder),
                dp(theme.generalStyle.roundCorner),
            )
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            add(recyclerView, lParams(matchParent, matchParent))
        }
        add(rightPanel, lParams(0, matchParent, weight = 6f) { marginStart = context.dp(2) })

        rightPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onGridMeasured?.invoke(
                recyclerView.width,
                recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom,
            )
        }

        // 初始化按钮样式
        val textColor = ColorManager.getColor("key_text_color")
        lockIcon.setImageResource(R.drawable.ic_outline_lock_open_24)
        lockIcon.imageTintList = ColorStateList.valueOf(textColor)
        returnIcon.setImageResource(R.drawable.ic_baseline_arrow_back_24)
        returnIcon.imageTintList = ColorStateList.valueOf(textColor)
        setLocked(false)
    }

    private fun createIcon(): ImageView = imageView {
        scaleType = ImageView.ScaleType.CENTER
    }

    private fun createIconButton(icon: ImageView): GestureFrame = view(::GestureFrame) {
        val content = constraintLayout {
            background = ColorManager.getDecorDrawable(
                "key_back_color",
                "key_border_color",
                dp(theme.generalStyle.keyBorder),
                dp(theme.generalStyle.roundCorner),
            )
            add(
                icon,
                lParams(wrapContent, wrapContent) {
                    centerInParent()
                },
            )
        }
        add(content, lParams(matchParent, matchParent))
    }

    fun setLocked(locked: Boolean) {
        lockIcon.setImageResource(
            if (locked) R.drawable.ic_outline_lock_24 else R.drawable.ic_outline_lock_open_24,
        )
        lockIcon.imageTintList = ColorStateList.valueOf(
            ColorManager.getColor(if (locked) "hilited_key_text_color" else "key_text_color"),
        )
    }
}
