/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

class LiquidKeyboardNavBar(
    private val context: Context,
    private val theme: Theme,
) {
    private val size = context.dp(theme.generalStyle.run { candidateViewHeight + commentHeight })
    private val space = context.dp(theme.liquidKeyboard.marginX).toInt()
    private val backForeground = theme.toolBar.buttons.firstOrNull()?.foreground ?: ToolBar.Button.Foreground()
    private val backIconWidth = context.dp(24f).coerceAtLeast(context.dp(backForeground.fontSize)) // ICON_ONLY
    private val endEdgeMargin = ((size - backIconWidth - context.dp(backForeground.padding)) / 2).toInt()

    fun createLiquidNavBar(
        fixedKeys: List<LiquidKeyboard.FixedKeyItem>,
        actionListener: CommonKeyboardActionListener,
    ): View = context.constraintLayout {
        val fixedKeysView = createFixedKeysContainer(fixedKeys, actionListener)
        add(
            fixedKeysView,
            lParams(wrapContent, matchParent) {
                endOfParent()
                centerVertically()
            },
        )
    }

    private fun createFixedKeysContainer(
        fixedKeys: List<LiquidKeyboard.FixedKeyItem>,
        actionListener: CommonKeyboardActionListener,
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fixedKeys.forEachIndexed { index, keyItem ->
            val keyName = keyItem.click
            if (keyName.isEmpty()) return@forEachIndexed

            val presetKey = theme.presetKeys[keyName]

            val baseButtonConfig = theme.toolBar.buttons.firstOrNull()
            val buttonConfig = if (baseButtonConfig != null) {
                baseButtonConfig.copy(
                    foreground = baseButtonConfig.foreground.copy(
                        style = when {
                            keyItem.label.isNotEmpty() -> keyItem.label
                            presetKey != null -> presetKey.label.takeIf { it.isNotEmpty() } ?: ""
                            else -> ""
                        },
                    ),
                    action = keyName,
                )
            } else {
                ToolBar.Button(
                    foreground = ToolBar.Button.Foreground(
                        style = when {
                            keyItem.label.isNotEmpty() -> keyItem.label
                            presetKey != null -> presetKey.label.takeIf { it.isNotEmpty() } ?: ""
                            else -> ""
                        },
                    ),
                    action = keyName,
                )
            }
            val btn = ToolButton(context, buttonConfig).apply {
                isRepeatable = presetKey?.repeatable ?: false
                setOnClickListener {
                    actionListener.listener.onAction(
                        KeyActionManager.getAction(keyName),
                    )
                }
            }

            val isLastButton = index == fixedKeys.lastIndex

            btn.layoutParams = LinearLayout.LayoutParams(
                keyItem.width?.let { context.dp(it).toInt() } ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                keyItem.height?.let { context.dp(it).toInt() } ?: size,
            ).apply {
                marginEnd = when {
                    isLastButton -> {
                        if (!keyItem.isStringFormat && keyItem.margin?.right != null) {
                            context.dp(keyItem.margin.right).toInt()
                        } else {
                            endEdgeMargin
                        }
                    }
                    keyItem.isStringFormat -> space
                    else -> keyItem.margin?.right?.let { context.dp(it).toInt() } ?: 0
                }

                marginStart = when {
                    keyItem.isStringFormat && isLastButton && fixedKeys.size == 1 -> 0
                    keyItem.isStringFormat -> space
                    else -> keyItem.margin?.left?.let { context.dp(it).toInt() } ?: 0
                }

                if (!keyItem.isStringFormat) {
                    keyItem.margin?.let {
                        topMargin = context.dp(it.top).toInt()
                        bottomMargin = context.dp(it.bottom).toInt()
                    }
                    keyItem.padding?.let {
                        btn.setPadding(
                            context.dp(it.left).toInt(),
                            context.dp(it.top).toInt(),
                            context.dp(it.right).toInt(),
                            context.dp(it.bottom).toInt(),
                        )
                    }
                }
            }

            container.addView(btn)
        }

        return container
    }
}
