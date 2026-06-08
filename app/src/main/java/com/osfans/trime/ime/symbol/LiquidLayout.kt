// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter

@SuppressLint("ViewConstructor")
class LiquidLayout(
    context: Context,
    theme: Theme,
    commonKeyboardActionListener: CommonKeyboardActionListener,
) : ConstraintLayout(context) {
    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    private val space = context.dp(theme.liquidKeyboard.marginX).toInt()

    private val fixedKeyBar =
        constraintLayout {
            val fixedKeys =
                theme.liquidKeyboard.fixedKeyBar.keys
            if (fixedKeys.isNotEmpty()) {
                val btns =
                    Array(fixedKeys.size) { index ->
                        val keyItem = fixedKeys[index]
                        // click 属性作为按键标识符
                        val keyName = keyItem.click
                        val ui = LiquidItemUi(context, theme)

                        // 确保文本居中显示
                        ui.mainText.gravity = gravityCenter

                        // 设置显示文本：优先使用 label，其次使用 presetKeys 的 label，最后使用 keyName
                        ui.mainText.text = when {
                            keyItem.label.isNotEmpty() -> keyItem.label
                            keyName.isNotEmpty() -> theme.presetKeys[keyName]?.label ?: ""
                            else -> ""
                        }

                        // 应用自定义内边距：支持四个方向
                        keyItem.padding?.let { insets ->
                            ui.root.setPadding(
                                dp(insets.left).toInt(),
                                dp(insets.top).toInt(),
                                dp(insets.right).toInt(),
                                dp(insets.bottom).toInt(),
                            )
                        }

                        // 处理按键点击和长按重复触发
                        val keyAction = if (keyName.isNotEmpty()) {
                            KeyActionManager.getAction(keyName)
                        } else {
                            null
                        }
                        ui.root.apply {
                            isRepeatable = keyAction?.isRepeatable == true
                            onClick = {
                                keyAction?.let {
                                    commonKeyboardActionListener.listener.onAction(it)
                                }
                            }
                        }

                        // 保存 keyItem 以便后续使用
                        ui.root.tag = keyItem

                        return@Array ui.root
                    }

                // 辅助函数：设置按键的 margin
                fun setBtnMargin(btn: android.view.View, insets: LiquidKeyboard.EdgeInsets?, isVertical: Boolean) {
                    btn.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        insets?.let {
                            leftMargin = dp(it.left).toInt()
                            topMargin = dp(it.top).toInt()
                            rightMargin = dp(it.right).toInt()
                            bottomMargin = dp(it.bottom).toInt()
                        } ?: run {
                            // 根据方向设置默认边距
                            if (isVertical) {
                                topMargin = space
                                bottomMargin = space
                            } else {
                                leftMargin = space
                                rightMargin = space
                            }
                        }
                    }
                }
                when (theme.liquidKeyboard.fixedKeyBar.position) {
                    LiquidKeyboard.KeyBar.Position.LEFT,
                    LiquidKeyboard.KeyBar.Position.RIGHT,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            val keyItem = btn.tag as? LiquidKeyboard.FixedKeyItem
                            val btnInsets = keyItem?.margin
                            add(
                                btn,
                                lParams(
                                    keyItem?.width?.let { dp(it).toInt() } ?: wrapContent,
                                    keyItem?.height?.let { dp(it).toInt() } ?: matchConstraints,
                                ) {
                                    if (i == 0) {
                                        topOfParent()
                                    } else {
                                        below(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        bottomOfParent()
                                    } else {
                                        above(btns[i + 1])
                                    }
                                },
                            )
                            setBtnMargin(btn, btnInsets, true)
                        }
                    }
                    LiquidKeyboard.KeyBar.Position.TOP,
                    LiquidKeyboard.KeyBar.Position.BOTTOM,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            val keyItem = btn.tag as? LiquidKeyboard.FixedKeyItem
                            val btnInsets = keyItem?.margin
                            add(
                                btn,
                                lParams(
                                    keyItem?.width?.let { dp(it).toInt() } ?: wrapContent,
                                    keyItem?.height?.let { dp(it).toInt() } ?: matchConstraints,
                                ) {
                                    if (i == 0) {
                                        startOfParent()
                                    } else {
                                        after(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        endOfParent()
                                    } else {
                                        before(btns[i + 1])
                                    }
                                },
                            )
                            setBtnMargin(btn, btnInsets, false)
                        }
                    }
                    LiquidKeyboard.KeyBar.Position.NAVBAR -> {
                        // NAVBAR 位置不需要添加按钮到 fixedKeyBar
                    }
                }
            }
        }

    val recyclerView =
        recyclerView {
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }

    val root = view(::FrameLayout) {
        add(recyclerView, lParams(matchParent, matchParent))
    }

    val tabsUi = LiquidTabsUi(context, theme)

    val isNavbarMode: Boolean =
        theme.liquidKeyboard.fixedKeyBar.position == LiquidKeyboard.KeyBar.Position.NAVBAR

    init {
        when (theme.liquidKeyboard.fixedKeyBar.position) {
            LiquidKeyboard.KeyBar.Position.TOP -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topToBottomOf(fixedKeyBar)
                        bottomOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(root)
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.BOTTOM -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(fixedKeyBar)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        topToBottomOf(root)
                        bottomOfParent()
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.LEFT -> {
                add(
                    root,
                    lParams {
                        centerVertically()
                        startToEndOf(fixedKeyBar)
                        endOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(root)
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.RIGHT -> {
                add(
                    root,
                    lParams {
                        centerVertically()
                        startOfParent()
                        endToStartOf(fixedKeyBar)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startToEndOf(root)
                        endOfParent()
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.NAVBAR -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(tabsUi.root)
                    },
                )
                add(
                    tabsUi.root,
                    lParams(matchParent, wrapContent) {
                        centerHorizontally()
                        bottomOfParent()
                    },
                )
            }
        }
    }
}
