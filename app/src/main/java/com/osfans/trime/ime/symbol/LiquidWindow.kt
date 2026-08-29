/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import org.kodein.di.instance

class LiquidWindow :
    BoardWindow.BarBoardWindow(),
    ResidentWindow {
    override val title: String
        get() = context.getString(R.string.liquid_symbol_panel)

    override val showTitle: Boolean = true

    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    var locked: Boolean = false
        private set

    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    var currentDataType: LiquidData.Type = LiquidData.Type.SINGLE
        private set
    private var currentTabIndex: Int = -1
    private var activePanelLabel: String? = null

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
                        commitText(this.text, recordHistory = true)
                    }
                }

                LiquidData.Type.TABS -> {
                    val realPosition = LiquidData.getTagList()
                        .indexOfFirst { it.label == this.text }
                    setDataByIndex(realPosition)
                }

                else -> {
                    commitText(this.text, recordHistory = currentDataType != LiquidData.Type.HISTORY)
                }
            }
        }
    }

    private val columns: Int
        get() = theme.liquidKeyboard.columns.coerceAtLeast(1)

    private val rows: Int
        get() = theme.liquidKeyboard.rows.coerceAtLeast(1)

    private val layoutManager by lazy {
        GridLayoutManager(context, columns)
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, theme).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(LiquidData.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        recyclerView.apply {
            layoutManager = this@LiquidWindow.layoutManager
            this.adapter = this@LiquidWindow.adapter
        }
        onGridMeasured = { _, height ->
            adapter.itemHeightPx = height / rows - gridDividerSize
        }
        lockButton.setOnClickListener {
            locked = !locked
            liquidLayout.setLocked(locked)
        }
        returnButton.setOnClickListener {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onCreateBarView(): View? = null

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

        val data = when (tag.type) {
            LiquidData.Type.HISTORY -> {
                symbolHistory.load()
                symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) }
            }

            else -> {
                LiquidData.getDataByIndex(i)
            }
        }
        if (tag.type == LiquidData.Type.TABS) {
            adapter.activePosition = data.indexOfFirst { it.text == activePanelLabel }
        } else {
            adapter.activePosition = RecyclerView.NO_POSITION
            activePanelLabel = tag.label
        }
        submitData(data)
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        val screen = rows * columns
        val remainder = data.size % columns
        val target =
            if (data.size < screen) {
                screen
            } else {
                data.size + if (remainder == 0) 0 else columns - remainder
            }
        adapter.submitList(data + List(target - data.size) { LiquidKeyboard.KeyItem("", "") })
    }

    private fun commitText(
        text: String,
        recordHistory: Boolean,
    ) {
        service.commitText(text)
        if (recordHistory) {
            symbolHistory.insert(text)
            symbolHistory.save()
        }
        if (!locked) {
            windowManager.attachWindow(KeyboardWindow)
        }
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
                if (!locked) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }
        }
    }
}