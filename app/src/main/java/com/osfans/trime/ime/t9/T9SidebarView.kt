package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.util.sp
import splitties.dimensions.dp

class T9SidebarView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
) : FrameLayout(context) {

    var onItemSelected: ((T9InputController.PinYinToken) -> Unit)? = null
    var onSymbolSelected: ((String) -> Unit)? = null

    private val scrollView = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = true
    }

    private val itemContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private var tokens: List<T9InputController.PinYinToken> = emptyList()
    private val defaultSymbols: List<String> = keyboard.t9SidebarSymbols

    private val sideBackColor: Int by lazy {
        resolveColor("t9_side_back_color", "key_back_color")
    }
    private val sideHilitedBackColor: Int by lazy {
        resolveColor("t9_side_hilited_back_color", "hilited_key_back_color")
    }
    private val sideTextColor: Int by lazy {
        resolveColor("t9_side_text_color", "key_text_color")
    }
    private val sideBorderColor: Int by lazy {
        resolveColor("t9_side_border_color", "key_border_color")
    }
    private val sideSpacingColor: Int by lazy {
        resolveColor("t9_side_spacing_color", "key_border_color")
    }

    private fun resolveColor(primaryKey: String, fallbackKey: String): Int {
        runCatching { ColorManager.getColor(primaryKey) }.getOrNull()?.let { return it }
        return runCatching { ColorManager.getColor(fallbackKey) }.getOrElse { Color.TRANSPARENT }
    }

    private val borderWidthPx: Int get() = context.dp(keyboard.keyBorder)
    private val sideCornerRadiusPx: Float get() = context.dp(keyboard.t9SideRoundCorner.toInt()).toFloat()
    private val verticalGap: Int get() = keyboard.verticalGap
    private val horizontalGap: Int get() = keyboard.horizontalGap
    private val isRight: Boolean get() = keyboard.t9SidebarPosition == "right"
    private val showItemCount: Int get() = keyboard.t9SidebarShowItems
    private val preferredHeight: Int get() = keyboard.getT9SidebarHeight()

    private val sideTextSizeSp: Float get() = sp(keyboard.t9SideTextSize)
    private val sideTypeface by lazy { FontManager.getTypeface("t9_side_text_font") }

    private val sidebarBg: GradientDrawable by lazy {
        GradientDrawable().apply {
            setColor(sideBackColor)
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx, sideBorderColor)
            }
            cornerRadius = sideCornerRadiusPx
        }
    }

    init {
        setWillNotDraw(false)
        val vGap = verticalGap
        val hGap = horizontalGap
        setPadding(
            if (isRight) hGap / 2 else 0,
            vGap,
            if (isRight) 0 else hGap / 2,
            vGap / 2,
        )

        scrollView.addView(
            itemContainer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP
            },
        )

        addView(
            scrollView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ).apply {
                gravity = Gravity.TOP
            },
        )
    }

    fun updateItems(items: List<T9InputController.PinYinToken>) {
        tokens = items
        itemContainer.removeAllViews()

        if (items.isEmpty() && defaultSymbols.isNotEmpty()) {
            showDefaultSymbols()
            return
        }
        if (items.isEmpty()) return

        val itemHeight = computeItemHeight()

        items.forEachIndexed { index, token ->
            val itemView = createItemView(token, itemHeight)
            itemContainer.addView(itemView)
            if (index < items.size - 1) {
                itemContainer.addView(createDivider())
            }
        }
    }

    private fun showDefaultSymbols() {
        if (defaultSymbols.isEmpty()) return
        val itemHeight = computeItemHeight()
        defaultSymbols.forEachIndexed { index, symbol ->
            val itemView = createSymbolItemView(symbol, itemHeight)
            itemContainer.addView(itemView)
            if (index < defaultSymbols.size - 1) {
                itemContainer.addView(createDivider())
            }
        }
    }

    private fun createSymbolItemView(symbol: String, itemHeight: Int): View {
        val label = TextView(context).apply {
            text = symbol
            setTextColor(sideTextColor)
            textSize = sideTextSizeSp
            gravity = Gravity.CENTER
            typeface = sideTypeface
            fontFeatureSettings = FontManager.fontFeatureSettings
            includeFontPadding = false
        }
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                itemHeight,
            )
            val vGap = verticalGap
            setPadding(0, vGap / 2, 0, vGap / 2)
            isClickable = true
            background = createPressStateDrawable()
            addView(
                label,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER
                },
            )
            setOnClickListener {
                onSymbolSelected?.invoke(symbol)
            }
        }
    }

    private fun computeItemHeight(): Int {
        val availableHeight = preferredHeight - paddingTop - paddingBottom
        if (availableHeight <= 0) return (context.dp(40)).coerceAtLeast(1)
        val count = showItemCount.coerceAtLeast(1)
        return (availableHeight / count)
    }

    override fun onDraw(canvas: Canvas) {
        sidebarBg.setBounds(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        sidebarBg.draw(canvas)
        super.onDraw(canvas)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val specH = MeasureSpec.getSize(heightMeasureSpec)
        if (measuredHeight < specH) {
            setMeasuredDimension(measuredWidth, specH)
        }
    }

    private fun createItemView(
        token: T9InputController.PinYinToken,
        itemHeight: Int,
    ): View {
        val label = TextView(context).apply {
            text = token.display
            setTextColor(sideTextColor)
            textSize = sideTextSizeSp
            gravity = Gravity.CENTER
            typeface = sideTypeface
            fontFeatureSettings = FontManager.fontFeatureSettings
            includeFontPadding = false
        }

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                itemHeight,
            )
            val vGap = verticalGap
            setPadding(0, vGap / 2, 0, vGap / 2)
            isClickable = true
            background = createPressStateDrawable()

            addView(
                label,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER
                },
            )

            setOnClickListener {
                onItemSelected?.invoke(token)
            }
        }
    }

    private fun createDivider(): View =
        View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dp(1),
                )
            setBackgroundColor(sideSpacingColor)
            alpha = 0.3f
        }

    private fun createPressStateDrawable(): StateListDrawable {
        val pressed = GradientDrawable().apply {
            setColor(sideHilitedBackColor)
            cornerRadius = sideCornerRadiusPx
        }
        val normal = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = sideCornerRadiusPx
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }
}
