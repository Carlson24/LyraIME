/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.bar.ui.LiquidKeyboardNavBar
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import org.kodein.di.instance
import splitties.dimensions.dp

class LiquidWindow :
    BoardWindow.BarBoardWindow(),
    ResidentWindow {
    override val showTitle: Boolean
        get() = liquidLayout.isNavbarMode

    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by di.instance()
    private val navBar by lazy { LiquidKeyboardNavBar(context, theme) }

    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    var currentDataType: LiquidData.Type = LiquidData.Type.SINGLE
        private set
    private var currentTabIndex: Int = -1

    private val adapter by lazy {
        LiquidAdapter(theme) {
            when (currentDataType) {
                LiquidData.Type.SYMBOL -> triggerSymbolInput(this.text)
                LiquidData.Type.VAR_LENGTH -> {
                    // VAR_LENGTH类型：如果altText和text不同，说明是键值对，像SYMBOL一样处理
                    // 如果altText和text相同，说明是纯文本，像SINGLE一样直接上屏
                    if (this.altText != this.text) {
                        triggerSymbolInput(this.text)
                    } else {
                        service.commitText(this.text)
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
                LiquidData.Type.TABS -> {
                    val realPosition = LiquidData.getTagList()
                        .indexOfFirst { it.label == this.text }
                    setDataByIndex(realPosition)
                }
                else -> {
                    service.commitText(this.text)
                    if (currentDataType != LiquidData.Type.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
            }
        }
    }

    private val mainLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, theme, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(LiquidData.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        recyclerView.apply {
            layoutManager = mainLayoutManager
            this.adapter = this@LiquidWindow.adapter
        }
    }

    override fun onCreateBarView(): View? = if (liquidLayout.isNavbarMode) {
        navBar.createLiquidNavBar(
            fixedKeys = theme.liquidKeyboard.fixedKeyBar.keys,
            actionListener = commonKeyboardActionListener,
        )
    } else {
        liquidLayout.tabsUi.root
    }

    override fun onAttached() {}

    override fun onDetached() {}

    fun setDataByIndex(i: Int) {
        if (currentTabIndex == i) {
            return
        }
        currentTabIndex = i

        val tag = LiquidData.getTagList()[i]
        currentDataType = tag.type
        liquidLayout.tabsUi.activateTab(i)

        // 更新 adapter 的数据类型
        val dataTypeChanged = adapter.currentDataType != currentDataType
        adapter.currentDataType = currentDataType

        // 根据数据类型动态设置 LayoutManager 的 justifyContent
        mainLayoutManager.justifyContent =
            if (LiquidData.isVarLengthType(currentDataType)) {
                JustifyContent.SPACE_BETWEEN
            } else {
                JustifyContent.FLEX_START
            }

        // 数据类型改变时，先通知刷新以重新创建 ViewHolder，然后再提交数据
        if (dataTypeChanged) adapter.notifyDataSetChanged()

        val data = when (tag.type) {
            LiquidData.Type.HISTORY -> {
                symbolHistory.load()
                symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) }
            }
            else -> {
                LiquidData.getDataByIndex(i)
            }
        }

        if (LiquidData.isVarLengthType(currentDataType)) {
            val originalData = data
            val expectedTabIndex = currentTabIndex
            val expectedDataType = currentDataType

            liquidLayout.recyclerView.post {
                if (currentTabIndex != expectedTabIndex || currentDataType != expectedDataType) {
                    return@post
                }
                val dataWithPlaceholders = originalData.toMutableList()
                val placeholderCount = calculatePlaceholderCount()
                repeat(placeholderCount) {
                    dataWithPlaceholders.add(LiquidKeyboard.KeyItem("", ""))
                }
                submitData(dataWithPlaceholders)
            }
        } else {
            submitData(data)
        }
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        adapter.submitList(data)
    }

    private fun calculatePlaceholderCount(): Int {
        val recyclerView = liquidLayout.recyclerView
        val containerWidth = recyclerView.width
        if (containerWidth <= 0) return 0

        val singleWidth = context.dp(theme.liquidKeyboard.singleWidth)
        val marginX = context.dp(theme.liquidKeyboard.marginX).toInt()
        val itemTotalWidth = singleWidth + 2 * marginX
        if (itemTotalWidth <= 0) return 0

        return containerWidth / itemTotalWidth
    }

    private fun triggerSymbolInput(symbol: String) {
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunct }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", false)
            it.clearComposition()
            it.simulateKeySequence(symbol)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", true)
            ContextCompat.getMainExecutor(service).execute {
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }
}
