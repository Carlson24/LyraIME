/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.isLandscape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import kotlin.math.abs

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    private val inputDepMgr = InputDependencyManager.initialize(this, themedContext, theme, service, rime)
    private val di = inputDepMgr.di
    private val broadcaster: InputBroadcaster by di.instance()
    private val popup: PopupDelegate by di.instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by di.instance()
    private val preedit: PreeditDelegate by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val inputBar: InputBarDelegate by di.instance()
    private val keyboardWindow: KeyboardWindow by di.instance()
    private val liquidWindow: LiquidWindow by di.instance()

    private val inlinePreeditMode by AppPrefs.defaultInstance().general.inlinePreeditMode
    private val appPrefs = AppPrefs.defaultInstance()
    private val effectiveWindowMode get() = service.inputDeviceManager.effectiveWindowMode
    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val keyboardSidePadding = theme.generalStyle.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
    private val keyboardBottomPadding = theme.generalStyle.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.generalStyle.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private fun broadcastKeyAppearanceUpdate() {
        val composing = rime.run { statusCached.isComposing }
        val hasMenu = rime.run { hasMenu }
        val paging = rime.run { paging }
        val current = Triple(composing, hasMenu, paging)
        if (current != lastAppearanceState) {
            lastAppearanceState = current
            broadcaster.onKeyAppearanceUpdate(current.first, current.second, current.third)
        }
    }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    var isFloating = false
        private set

    private val isLandscapeOrientation: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private val isEffectiveFloating: Boolean
        get() = isFloating

    var isOneHanded = false
        private set

    private var oneHandOnRight = true

    private var oneHandResizeStartWidth = 0
    private var lastOneHandTouchX = 0f
    private var oneHandDragging = false
    private var lastOneHandGapRefreshAt = 0L
    private val oneHandGapRefreshIntervalMs = 80L
    private val oneHandTouchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private val internalPrefs = appPrefs.internal

    private var oneHandOnRightPortrait by internalPrefs.oneHandOnRightPortrait
    private var oneHandOnRightLandscape by internalPrefs.oneHandOnRightLandscape
    private var oneHandWidthPx by internalPrefs.oneHandWidthPx

    private var floatingWidthPx by internalPrefs.floatingKeyboardWidth
    private var floatingHeightPx by internalPrefs.floatingKeyboardHeight
    private var floatingXPortrait by internalPrefs.floatingKeyboardXPortrait
    private var floatingYPortrait by internalPrefs.floatingKeyboardYPortrait
    private var floatingXLandscape by internalPrefs.floatingKeyboardXLandscape
    private var floatingYLandscape by internalPrefs.floatingKeyboardYLandscape

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var floatingResizeStartWidth = 0
    private var floatingResizeStartHeight = 0
    private var lastResizeTouchX = 0f
    private var lastResizeTouchY = 0f

    private val minFloatingWidthPx: Int
        get() = dp(180).coerceAtMost(resources.displayMetrics.widthPixels)

    private val maxFloatingWidthPx: Int
        get() = resources.displayMetrics.widthPixels.coerceAtLeast(minFloatingWidthPx)

    private val minFloatingHeightPx: Int
        get() = dp(100).coerceAtMost(resources.displayMetrics.heightPixels)

    private val maxFloatingHeightPx: Int
        get() = (resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(minFloatingHeightPx)

    private val floatingCornerRadiusPx: Int
        get() = dp(10)

    private val keyboardOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return
            val radius = if (isEffectiveFloating) floatingCornerRadiusPx.toFloat() else 0f
            outline.setRoundRect(0, 0, width, height, radius)
        }
    }

    private fun resolveFloatingWidth(): Int {
        val stored = floatingWidthPx.takeIf { it > 0 } ?: run {
            val default = (resources.displayMetrics.widthPixels * 0.8).toInt()
            floatingWidthPx = default.coerceIn(minFloatingWidthPx, maxFloatingWidthPx)
            floatingWidthPx
        }
        floatingWidthPx = stored.coerceIn(minFloatingWidthPx, maxFloatingWidthPx)
        return floatingWidthPx
    }

    private val defaultKeyboardHeightPx: Int
        get() {
            val keyboard = runCatching { KeyboardWindow.currentKeyboard }.getOrNull()
            if (keyboard != null && keyboard.height > 0) {
                return keyboard.height
            }
            var h = theme.generalStyle.keyboardHeight
            if (context.isLandscapeMode()) {
                val land = theme.generalStyle.keyboardHeightLand
                if (land > 0) h = land
            }
            return dp(h)
        }

    private fun resolveFloatingHeight(): Int {
        val stored = floatingHeightPx.takeIf { it > 0 } ?: run {
            floatingHeightPx = defaultKeyboardHeightPx.coerceIn(minFloatingHeightPx, maxFloatingHeightPx)
            floatingHeightPx
        }
        floatingHeightPx = stored.coerceIn(minFloatingHeightPx, maxFloatingHeightPx)
        return floatingHeightPx
    }

    private fun getStoredFloatingPosition(): Pair<Int, Int> = if (isLandscapeOrientation) {
        floatingXLandscape to floatingYLandscape
    } else {
        floatingXPortrait to floatingYPortrait
    }

    private fun saveFloatingPosition(x: Int, y: Int) {
        if (isLandscapeOrientation) {
            floatingXLandscape = x
            floatingYLandscape = y
        } else {
            floatingXPortrait = x
            floatingYPortrait = y
        }
    }

    val keyboardView: ConstraintLayout

    private fun handleColor(): Int {
        val base = ContextCompat.getColor(context, R.color.lavender)
        return (base and 0x00FFFFFF) or (0x40 shl 24)
    }

    private fun createHandleDrawable(radius: Float? = null, color: Int = handleColor()): GradientDrawable {
        val r = radius ?: dp(3).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(dp(6), dp(6))
            cornerRadius = r
            setColor(color)
        }
    }

    private val floatingRightHandle =
        view(::View) {
            visibility = View.GONE
            // TODO: T9 sidebar text disappears when resizing width in floating mode,
            //  because KeyboardWindow.refreshKeyboards() is triggered on every layout change.
            setOnTouchListener { v, event ->
                if (!isFloating) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        floatingResizeStartWidth = resolveFloatingWidth()
                        lastResizeTouchX = event.rawX
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = (event.rawX - lastResizeTouchX).toInt()
                        floatingWidthPx =
                            (floatingResizeStartWidth + delta).coerceIn(minFloatingWidthPx, maxFloatingWidthPx)
                        applyFloatingWidth()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.isPressed = false
                        saveFloatingPosition(
                            keyboardView.translationX.toInt(),
                            keyboardView.translationY.toInt(),
                        )
                        true
                    }
                    else -> false
                }
            }
        }

    private val floatingBottomHandle =
        view(::View) {
            visibility = View.GONE
            setOnTouchListener { v, event ->
                if (!isEffectiveFloating) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        floatingResizeStartHeight = resolveFloatingHeight()
                        lastResizeTouchY = event.rawY
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = (event.rawY - lastResizeTouchY).toInt()
                        floatingHeightPx =
                            (floatingResizeStartHeight + delta).coerceIn(minFloatingHeightPx, maxFloatingHeightPx)
                        applyFloatingHeight()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.isPressed = false
                        saveFloatingPosition(
                            keyboardView.translationX.toInt(),
                            keyboardView.translationY.toInt(),
                        )
                        true
                    }
                    else -> false
                }
            }
        }

    private val adjustableHandle =
        view(::View) {
            visibility = View.GONE
            setOnTouchListener { v, event ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        keyboardView.translationX += dx
                        keyboardView.translationY += dy
                        clampFloatingPosition()
                        preedit.ui.root.translationX = keyboardView.translationX
                        preedit.ui.root.translationY = keyboardView.translationY
                        updateHandlePosition()
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.isPressed = false
                        saveFloatingPosition(
                            keyboardView.translationX.toInt(),
                            keyboardView.translationY.toInt(),
                        )
                        true
                    }
                    else -> false
                }
            }
        }

    private val oneHandHandle =
        view(::View) {
            visibility = View.GONE
            setOnClickListener {
                if (!isDockedOneHandMode) return@setOnClickListener
                switchOneHandSide()
            }
            setOnTouchListener { v, event ->
                if (!isDockedOneHandMode) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        oneHandResizeStartWidth = resolveOneHandWidth()
                        lastOneHandTouchX = event.rawX
                        oneHandDragging = false
                        lastOneHandGapRefreshAt = 0L
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = event.rawX - lastOneHandTouchX
                        if (!oneHandDragging && abs(delta) > oneHandTouchSlop) {
                            oneHandDragging = true
                        }
                        if (oneHandDragging) {
                            val target = if (oneHandOnRight) {
                                oneHandResizeStartWidth - delta.toInt()
                            } else {
                                oneHandResizeStartWidth + delta.toInt()
                            }
                            oneHandWidthPx = target.coerceIn(minOneHandWidthPx, maxOneHandWidthPx)
                            applyOneHandWidth()
                            updateOneHandGapScale()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.isPressed = false
                        if (!oneHandDragging && event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        } else if (oneHandDragging) {
                            updateOneHandGapScale(force = true)
                        }
                        oneHandDragging = false
                        true
                    }
                    else -> false
                }
            }
        }

    private val isDockedOneHandMode: Boolean
        get() = isOneHanded && !isFloating

    private val minOneHandWidthPx: Int
        get() = dp(180).coerceAtMost(resources.displayMetrics.widthPixels)

    private val maxOneHandWidthPx: Int
        get() = resources.displayMetrics.widthPixels.coerceAtLeast(minOneHandWidthPx)

    private fun resolveOneHandWidth(): Int {
        val stored = oneHandWidthPx.takeIf { it > 0 } ?: run {
            val default = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            oneHandWidthPx = default.coerceIn(minOneHandWidthPx, maxOneHandWidthPx)
            oneHandWidthPx
        }
        oneHandWidthPx = stored.coerceIn(minOneHandWidthPx, maxOneHandWidthPx)
        return oneHandWidthPx
    }

    private fun getStoredOneHandSide(): Boolean = if (isLandscapeOrientation) {
        oneHandOnRightLandscape
    } else {
        oneHandOnRightPortrait
    }

    private fun saveOneHandSide(onRight: Boolean) {
        if (isLandscapeOrientation) {
            oneHandOnRightLandscape = onRight
        } else {
            oneHandOnRightPortrait = onRight
        }
    }

    private fun switchOneHandSide() {
        oneHandOnRight = !oneHandOnRight
        saveOneHandSide(oneHandOnRight)
        if (!isDockedOneHandMode) return
        updateKeyboardSize()
        syncOneHandHandleUi(bringToFront = true)
        requestLayout()
    }

    private fun applyOneHandWidth() {
        if (!isDockedOneHandMode) return
        updateKeyboardSize()
        updateOneHandHandlePosition()
        requestLayout()
    }

    private fun updateOneHandGapScale(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastOneHandGapRefreshAt < oneHandGapRefreshIntervalMs) {
            return
        }
        lastOneHandGapRefreshAt = now
        if (!isDockedOneHandMode) {
            keyboardWindow.setHorizontalGapScale(1f)
            return
        }
        val containerWidth = keyboardView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val scale = resolveOneHandWidth().toFloat() / containerWidth.toFloat()
        keyboardWindow.setHorizontalGapScale(scale)
    }

    private fun updateOneHandHandleAppearance() {
        val color = handleColor()
        val background = createHandleDrawable(dp(10).toFloat(), color)
        val iconRes = if (oneHandOnRight) {
            R.drawable.ic_baseline_keyboard_arrow_left_24
        } else {
            R.drawable.ic_baseline_keyboard_arrow_right_24
        }
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (icon == null) {
            oneHandHandle.background = background
            return
        }
        icon.setTint(color)
        val inset = dp(4)
        val drawable = LayerDrawable(arrayOf(background, icon)).apply {
            setLayerInset(1, inset, inset, inset, inset)
        }
        oneHandHandle.background = drawable
    }

    private fun updateOneHandHandlePosition() {
        val safeGap = dp(6)
        oneHandHandle.updateLayoutParams<LayoutParams> {
            topToTop = windowManager.view.id
            bottomToBottom = windowManager.view.id
            if (oneHandOnRight) {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
                startToEnd = ConstraintLayout.LayoutParams.UNSET
                endToStartOf(windowManager.view)
                marginStart = 0
                marginEnd = safeGap
            } else {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
                endToStart = ConstraintLayout.LayoutParams.UNSET
                startToEndOf(windowManager.view)
                marginStart = safeGap
                marginEnd = 0
            }
        }
    }

    private fun updateOneHandHandleVisibility() {
        oneHandHandle.visibility = if (isOneHanded && !isFloating) View.VISIBLE else View.GONE
    }

    private fun syncOneHandHandleUi(bringToFront: Boolean = false) {
        updateOneHandHandleAppearance()
        updateOneHandHandlePosition()
        updateOneHandHandleVisibility()
        if (bringToFront) {
            oneHandHandle.bringToFront()
        }
    }

    init {
        // MUST call before any operation
        inputDepMgr.start()

        isOneHanded = rime.run { getRuntimeOption("_one_hand_mode") }
        oneHandOnRight = getStoredOneHandSide()

        isFloating = rime.run { getRuntimeOption("_floating_keyboard") }

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")

        keyboardView =
            constraintLayout {
                id = View.generateViewId()
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams(matchParent, matchParent) {
                        startOfParent()
                        endOfParent()
                        topOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(inputBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
                add(
                    oneHandHandle,
                    lParams(dp(16), dp(44)) {
                        topToTop = windowManager.view.id
                        bottomToBottom = windowManager.view.id
                        startToEndOf(windowManager.view)
                    },
                )
            }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = if (isEffectiveFloating) resolveFloatingHeight() else it
                    }
                    if (isEffectiveFloating) {
                        applyKeyboardViewScale()
                    } else {
                        keyboardWindow.currentKeyboardView?.apply {
                            scaleY = 1f
                            scaleX = 1f
                        }
                    }
                }
            }

        updateKeyboardSize()

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardView)
                startOfParent()
            },
        )

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )

        keyboardView.clipToOutline = true
        keyboardView.outlineProvider = keyboardOutlineProvider

        add(
            floatingRightHandle,
            lParams(dp(10), dp(10)) {
                startOfParent()
                topOfParent()
            },
        )
        add(
            floatingBottomHandle,
            lParams(dp(10), dp(10)) {
                startOfParent()
                topOfParent()
            },
        )
        add(
            adjustableHandle,
            lParams(dp(24), dp(24)) {
                startOfParent()
                topOfParent()
            },
        )
        updateFloatingHandlesVisibility()

        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )

        inputBar.view.setOnTouchListener { v, event ->
            if (!isFloating) return@setOnTouchListener false
            v.parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    keyboardView.translationX += dx
                    keyboardView.translationY += dy
                    clampFloatingPosition()
                    preedit.ui.root.translationX = keyboardView.translationX
                    preedit.ui.root.translationY = keyboardView.translationY
                    updateHandlePosition()
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    saveFloatingPosition(
                        keyboardView.translationX.toInt(),
                        keyboardView.translationY.toInt(),
                    )
                    true
                }
                else -> false
            }
        }

        applyFloatingLayout()
    }

    private fun updateKeyboardSize() {
        if (isEffectiveFloating) {
            val marginPx = dp(2)
            val bottomPx = dp(2)
            bottomPaddingSpace.visibility = View.VISIBLE
            bottomPaddingSpace.updateLayoutParams {
                height = bottomPx
            }
            leftPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = marginPx
            }
            rightPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.updateLayoutParams {
                width = marginPx
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
            inputBar.view.setPadding(marginPx, 0, marginPx, 0)
            return
        }

        bottomPaddingSpace.visibility = View.VISIBLE
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }

        if (isDockedOneHandMode) {
            val containerWidth = keyboardView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val oneHandWidth = resolveOneHandWidth().coerceAtMost(containerWidth)
            val remaining = (containerWidth - oneHandWidth).coerceAtLeast(0)

            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            if (oneHandOnRight) {
                leftPaddingSpace.updateLayoutParams {
                    width = remaining
                }
                rightPaddingSpace.updateLayoutParams {
                    width = 0
                }
            } else {
                leftPaddingSpace.updateLayoutParams {
                    width = 0
                }
                rightPaddingSpace.updateLayoutParams {
                    width = remaining
                }
            }

            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
            inputBar.view.setPadding(
                if (oneHandOnRight) remaining else 0,
                0,
                if (oneHandOnRight) 0 else remaining,
                0,
            )
            syncOneHandHandleUi()
            updateHandlePosition()
            updateOneHandGapScale()
            return
        }

        val sidePadding = keyboardSidePaddingPx
        if (sidePadding == 0) {
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = ConstraintLayout.LayoutParams.UNSET
                endToStart = ConstraintLayout.LayoutParams.UNSET
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        inputBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    fun updateInputBarVisibility() {
        inputBar.updateVisibility()
    }

    fun reloadColors() {
        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")
        keyboardWindow.invalidateAllCachedKeyColors()
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }

                if (it.data.option == "_one_hand_mode") {
                    toggleOneHandMode()
                }

                if (it.data.option == "_floating_keyboard") {
                    val floating = rime.run { getRuntimeOption("_floating_keyboard") }
                    if (floating != isFloating) {
                        isFloating = floating
                        if (floating && isOneHanded) {
                            isOneHanded = false
                        }
                        popup.dismissAll()
                        applyFloatingLayout()
                    }
                    keyboardWindow.currentKeyboardView?.invalidateAllKeys()
                }
            }
            is RimeMessage.CompositionMessage -> {
                val data = if (effectiveWindowMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }
            is RimeMessage.CandidateMenuMessage -> {
                broadcaster.onCandidateMenuUpdate(it.data)
            }
            is RimeMessage.CandidateListMessage -> {
                val data = if (effectiveWindowMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    RimeMessage.CandidateListMessage.Data()
                } else {
                    it.data
                }
                broadcaster.onCandidateListUpdate(data)
            }
            else -> {}
        }
        broadcastKeyAppearanceUpdate()
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean = inputBar.handleInlineSuggestions(response)

    fun stopVoiceRecognition() {
        inputBar.stopAsrkbVoiceFromToolbar()
    }

    fun toggleOneHandMode() {
        popup.dismissAll()
        if (isFloating) {
            saveFloatingPosition(
                keyboardView.translationX.toInt(),
                keyboardView.translationY.toInt(),
            )
            isFloating = false
            applyDockedLayout()
        }
        isOneHanded = !isOneHanded
        if (isOneHanded) {
            resolveOneHandWidth()
        }
        updateFloatingHandlesVisibility()
        updateOneHandHandleVisibility()
        updateKeyboardSize()
        updateOneHandGapScale(force = true)
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        inputBar.stopAsrkbVoiceFromToolbar()
        inputDepMgr.stop()
        super.onDetachedFromWindow()
    }

    private fun applyFloatingLayout() {
        val params = keyboardView.layoutParams as ConstraintLayout.LayoutParams
        if (isEffectiveFloating) {
            params.width = resolveFloatingWidth()
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
            keyboardView.layoutParams = params

            layoutParams?.height = matchParent

            applyFloatingHeight()

            bottomPaddingSpace.updateLayoutParams<LayoutParams> {
                bottomMargin = 0
            }

            val (storedX, storedY) = getStoredFloatingPosition()
            if (storedX != -1 && storedY != -1) {
                keyboardView.translationX = storedX.toFloat()
                keyboardView.translationY = storedY.toFloat()
            } else if (keyboardView.translationX == 0f && keyboardView.translationY == 0f) {
                keyboardView.translationX = (resources.displayMetrics.widthPixels * 0.1).toFloat()
                keyboardView.translationY = (resources.displayMetrics.heightPixels * 0.6).toFloat()
            }

            updateHandlePosition()
            keyboardView.post {
                clampFloatingPosition()
                updateHandlePosition()
            }

            preedit.ui.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = 0
                startToStart = keyboardView.id
                endToEnd = keyboardView.id
                bottomToTop = keyboardView.id
                topToBottom = ConstraintLayout.LayoutParams.UNSET
                topToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            }
            preedit.ui.root.translationX = keyboardView.translationX
            preedit.ui.root.translationY = keyboardView.translationY
        } else {
            applyDockedLayout()
        }
        updateKeyboardSize()
        if (!isEffectiveFloating) {
            requestApplyInsets()
        }
        updateHandlePosition()
        updateFloatingHandlesVisibility()
        updateOneHandHandleVisibility()
        keyboardView.invalidateOutline()
        requestLayout()
    }

    private fun applyDockedLayout() {
        keyboardWindow.currentKeyboardView?.apply {
            scaleY = 1f
            scaleX = 1f
        }

        val params = keyboardView.layoutParams as ConstraintLayout.LayoutParams
        params.matchConstraintMaxWidth = ConstraintLayout.LayoutParams.UNSET
        params.matchConstraintMinWidth = ConstraintLayout.LayoutParams.UNSET
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.UNSET
        params.width = matchParent
        params.startToEnd = ConstraintLayout.LayoutParams.UNSET
        params.endToStart = ConstraintLayout.LayoutParams.UNSET
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        keyboardView.layoutParams = params
        keyboardView.translationX = 0f
        keyboardView.translationY = 0f

        preedit.ui.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = wrapContent
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.UNSET
            bottomToTop = keyboardView.id
            topToBottom = ConstraintLayout.LayoutParams.UNSET
            topToTop = ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        }
        preedit.ui.root.translationX = 0f
        preedit.ui.root.translationY = 0f

        keyboardView.invalidateOutline()
    }

    private fun applyFloatingWidth() {
        keyboardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = resolveFloatingWidth()
        }
        keyboardView.invalidateOutline()
        requestLayout()
        updateHandlePosition()
    }

    private fun applyFloatingHeight() {
        val targetHeight = resolveFloatingHeight()
        windowManager.view.updateLayoutParams {
            height = targetHeight
        }
        applyKeyboardViewScale()
        keyboardView.invalidateOutline()
        requestLayout()
        updateHandlePosition()
    }

    private fun applyKeyboardViewScale() {
        val targetHeight = resolveFloatingHeight()
        val keyboard = runCatching { KeyboardWindow.currentKeyboard }.getOrNull()
        val kv = keyboardWindow.currentKeyboardView
        if (keyboard != null && kv != null && targetHeight > 0) {
            val layoutHeight = keyboard.keyboardHeight
            if (layoutHeight > 0) {
                kv.scaleY = targetHeight.toFloat() / layoutHeight.toFloat()
                kv.pivotY = 0f
            }
        }
    }

    private fun updateFloatingHandlesVisibility() {
        if (isEffectiveFloating) {
            floatingRightHandle.visibility = View.VISIBLE
            floatingBottomHandle.visibility = View.VISIBLE
            adjustableHandle.visibility = View.VISIBLE
        } else {
            floatingRightHandle.visibility = View.GONE
            floatingBottomHandle.visibility = View.GONE
            adjustableHandle.visibility = View.GONE
        }
    }

    private fun updateHandlePosition() {
        if (!isFloating) return

        val kX = keyboardView.translationX
        val kY = keyboardView.translationY
        val kWidth = if (keyboardView.width > 0) keyboardView.width else resolveFloatingWidth()
        val kHeight = if (keyboardView.height > 0) {
            keyboardView.height
        } else {
            resolveFloatingHeight() + dp(inputBar.themedHeight) + dp(2)
        }

        val handleThickness = dp(6)
        val handleLength = dp(48)
        val touchPadding = dp(12)
        val viewThickness = handleThickness + touchPadding * 2
        val viewLength = handleLength + touchPadding * 2

        floatingRightHandle.translationX = kX + kWidth - viewThickness / 2
        floatingRightHandle.translationY = kY + (kHeight - viewLength) / 2
        val rightDrawable = createHandleDrawable()
        floatingRightHandle.background = InsetDrawable(
            rightDrawable,
            touchPadding,
            touchPadding,
            touchPadding,
            touchPadding,
        )
        floatingRightHandle.updateLayoutParams {
            width = viewThickness
            height = viewLength
        }

        floatingBottomHandle.translationX = kX + (kWidth - viewLength) / 2
        floatingBottomHandle.translationY = kY + kHeight - viewThickness / 2
        val bottomDrawable = createHandleDrawable()
        floatingBottomHandle.background = InsetDrawable(
            bottomDrawable,
            touchPadding,
            touchPadding,
            touchPadding,
            touchPadding,
        )
        floatingBottomHandle.updateLayoutParams {
            width = viewLength
            height = viewThickness
        }

        val moveHandleSize = dp(24)
        adjustableHandle.translationX = kX + (kWidth - moveHandleSize) / 2
        adjustableHandle.translationY = kY - moveHandleSize - dp(8)
        val moveBgDrawable = createHandleDrawable(moveHandleSize / 2f)
        val moveIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_move_handle_cross)?.mutate()
        val finalDrawable = if (moveIconDrawable != null) {
            moveIconDrawable.setTint(handleColor())
            val inset = dp(4)
            val ld = LayerDrawable(arrayOf(moveBgDrawable, moveIconDrawable))
            ld.setLayerInset(1, inset, inset, inset, inset)
            ld
        } else {
            moveBgDrawable
        }
        adjustableHandle.background = finalDrawable
        adjustableHandle.updateLayoutParams {
            width = moveHandleSize
            height = moveHandleSize
        }
    }

    private fun clampFloatingPosition() {
        if (!isEffectiveFloating) return
        val containerWidth = if (width > 0) width else resources.displayMetrics.widthPixels
        val containerHeight = if (height > 0) height else resources.displayMetrics.heightPixels
        val keyboardWidth = if (keyboardView.width > 0) keyboardView.width else resolveFloatingWidth()
        val keyboardHeight = if (keyboardView.height > 0) {
            keyboardView.height
        } else {
            resolveFloatingHeight() + dp(inputBar.themedHeight) + dp(2) + keyboardBottomPaddingPx
        }

        val maxX = (containerWidth - keyboardWidth).coerceAtLeast(0)
        val maxY = (containerHeight - keyboardHeight).coerceAtLeast(0)
        val clampedX = keyboardView.translationX.coerceIn(0f, maxX.toFloat())
        val clampedY = keyboardView.translationY.coerceIn(0f, maxY.toFloat())

        if (clampedX != keyboardView.translationX || clampedY != keyboardView.translationY) {
            keyboardView.translationX = clampedX
            keyboardView.translationY = clampedY
        }
        preedit.ui.root.translationX = keyboardView.translationX
        preedit.ui.root.translationY = keyboardView.translationY
    }

    fun getFloatingKeyboardRegion(outRegion: Region) {
        if (!isEffectiveFloating) return
        val rect = Rect()

        keyboardView.getHitRect(rect)

        if (preedit.ui.root.visibility == View.VISIBLE) {
            val preeditRect = Rect()
            preedit.ui.root.getHitRect(preeditRect)
            rect.union(preeditRect)
        }

        if (floatingRightHandle.visibility == View.VISIBLE) {
            val handleRect = Rect()
            floatingRightHandle.getHitRect(handleRect)
            rect.union(handleRect)
        }

        if (floatingBottomHandle.visibility == View.VISIBLE) {
            val handleRect = Rect()
            floatingBottomHandle.getHitRect(handleRect)
            rect.union(handleRect)
        }

        if (adjustableHandle.visibility == View.VISIBLE) {
            val handleRect = Rect()
            adjustableHandle.getHitRect(handleRect)
            rect.union(handleRect)
        }

        outRegion.set(rect)
    }
}
