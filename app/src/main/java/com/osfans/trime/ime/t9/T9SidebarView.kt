package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.util.UnicodeVariantUtils
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
        clipChildren = true
        clipToOutline = true
        setPadding(borderWidthPx, borderWidthPx, borderWidthPx, borderWidthPx)
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radius = (sideCornerRadiusPx - borderWidthPx).coerceAtLeast(0f)
                    val inset = borderWidthPx.coerceAtLeast(0)
                    val left = inset
                    val top = inset
                    val right = view.width - inset
                    val bottom = view.height - inset
                    if (right <= left || bottom <= top) {
                        outline.setRect(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(left, top, right, bottom, radius)
                    }
                }
            }
    }

    private val itemContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private var tokens: List<T9InputController.PinYinToken> = emptyList()
    private val defaultSymbols: List<String> = keyboard.t9SidebarSymbols

    private val sidebarBg by lazy {
        ColorManager.getDecorDrawable(
            "t9_side_back_color",
            "t9_side_border_color",
            borderWidthPx,
            sideCornerRadiusPx,
        ) ?: GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx, sideBorderColor)
            }
            cornerRadius = sideCornerRadiusPx
        }
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

    private val sideHilitedBg by lazy {
        ColorManager.getDecorDrawable(
            "t9_side_hilited_back_color",
            cornerRadius = sideCornerRadiusPx,
        )
    }

    private val borderLayer = View(context).apply {
        background = sidebarBg
    }

    private fun resolveColor(primaryKey: String, fallbackKey: String): Int {
        runCatching { ColorManager.getColor(primaryKey) }.getOrNull()?.let { return it }
        return runCatching { ColorManager.getColor(fallbackKey) }.getOrElse { Color.TRANSPARENT }
    }

    private val borderWidthPx: Int get() = context.dp(keyboard.keyBorder)
    private val dividerHeightPx: Int get() = context.dp(1)
    private val sideCornerRadiusPx: Float get() = context.dp(keyboard.t9SideRoundCorner.toInt()).toFloat()
    private val verticalGap: Int get() = keyboard.verticalGap
    private val horizontalGap: Int get() = keyboard.horizontalGap
    private val isRight: Boolean get() = keyboard.t9SidebarPosition == "right"
    private val showItemCount: Int get() = keyboard.t9SidebarShowItems
    private val preferredHeight: Int get() = keyboard.getT9SidebarHeight()

    private val sideTextSizeSp: Float get() = sp(keyboard.t9SideTextSize)
    private val sideTypeface by lazy { FontManager.getTypeface("t9_side_font") }

    private val itemViewPool = ArrayDeque<FrameLayout>(8)
    private val dividerPool = ArrayDeque<View>(8)

    private inner class CjkCorrectedTextView(context: Context) : TextView(context) {
        override fun onDraw(canvas: Canvas) {
            val t = text?.toString() ?: ""
            if (t.isCjkPunctuation()) {
                canvas.save()
                canvas.translate(visualCenterCorrect(t, paint), 0f)
                super.onDraw(canvas)
                canvas.restore()
            } else {
                super.onDraw(canvas)
            }
        }
    }

    private fun String.isCjkPunctuation(): Boolean {
        if (length != 1) return false
        val c = this[0]
        return c in '\u3000'..'\u303F' ||
            c in '\uFF00'..'\uFF0F' ||
            c in '\uFF1A'..'\uFF1F' ||
            c == '\u00B7'
    }

    private fun visualCenterCorrect(text: String, paint: Paint): Float {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val glyphCenter = paint.measureText(text) / 2f
        val visualCenter = bounds.width() / 2f + bounds.left.toFloat()
        return glyphCenter - visualCenter
    }

    private companion object {
        private const val MODE_EMPTY = 0
        private const val MODE_SYMBOLS = 1
        private const val MODE_TOKENS = 2
    }

    private var currentMode = MODE_EMPTY

    init {
        val vGap = verticalGap
        val hGap = horizontalGap
        setPadding(
            hGap / 2,
            vGap / 2,
            hGap / 2,
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
            borderLayer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
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

        if (defaultSymbols.isNotEmpty()) {
            showDefaultSymbols()
        }
    }

    fun updateItems(items: List<T9InputController.PinYinToken>) {
        tokens = items

        if (items.isEmpty()) {
            if (defaultSymbols.isNotEmpty()) {
                if (currentMode != MODE_SYMBOLS) {
                    recycleAllViews()
                    showDefaultSymbols()
                }
            } else {
                recycleAllViews()
            }
            return
        }

        val itemHeight = computeItemHeight()

        if (currentMode != MODE_TOKENS) {
            recycleAllViews()
            buildTokenViews(items, itemHeight)
            return
        }

        updateTokenViewsIncremental(items, itemHeight)
    }

    private fun buildTokenViews(
        items: List<T9InputController.PinYinToken>,
        itemHeight: Int,
    ) {
        currentMode = MODE_TOKENS
        items.forEachIndexed { index, token ->
            itemContainer.addView(obtainItemView(token, itemHeight))
            if (index < items.size - 1) {
                itemContainer.addView(obtainDivider())
            }
        }
    }

    private fun updateTokenViewsIncremental(
        items: List<T9InputController.PinYinToken>,
        itemHeight: Int,
    ) {
        val targetChildCount = items.size * 2 - 1

        while (itemContainer.childCount > targetChildCount) {
            val idx = itemContainer.childCount - 1
            val child = itemContainer.getChildAt(idx)
            if (child is FrameLayout) {
                child.setOnClickListener(null)
                itemViewPool.addLast(child)
            } else {
                dividerPool.addLast(child)
            }
            itemContainer.removeViewAt(idx)
        }

        for (i in items.indices) {
            val itemIdx = i * 2
            val token = items[i]

            if (itemIdx < itemContainer.childCount) {
                val itemView = itemContainer.getChildAt(itemIdx) as? FrameLayout ?: continue
                itemView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeight,
                )
                (itemView.getChildAt(0) as? TextView)?.text = UnicodeVariantUtils.toDisplay(token.display)
                itemView.setOnClickListener { onItemSelected?.invoke(token) }
            } else {
                itemContainer.addView(obtainItemView(token, itemHeight))
            }

            if (i < items.size - 1 && itemIdx + 1 >= itemContainer.childCount) {
                itemContainer.addView(obtainDivider())
            }
        }
    }

    private fun showDefaultSymbols() {
        currentMode = MODE_SYMBOLS
        val itemHeight = computeItemHeight()
        defaultSymbols.forEachIndexed { index, symbol ->
            val itemView = createSymbolItemView(symbol, itemHeight)
            itemContainer.addView(itemView)
            if (index < defaultSymbols.size - 1) {
                itemContainer.addView(createDivider())
            }
        }
    }

    private fun recycleAllViews() {
        for (i in 0 until itemContainer.childCount) {
            val child = itemContainer.getChildAt(i)
            if (child is FrameLayout) {
                child.setOnClickListener(null)
                itemViewPool.addLast(child)
            } else {
                dividerPool.addLast(child)
            }
        }
        itemContainer.removeAllViews()
        currentMode = MODE_EMPTY
    }

    private fun obtainItemView(
        token: T9InputController.PinYinToken,
        itemHeight: Int,
    ): FrameLayout {
        val view = if (itemViewPool.isNotEmpty()) {
            itemViewPool.removeLast().also { v ->
                v.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeight,
                )
                (v.getChildAt(0) as? TextView)?.text = UnicodeVariantUtils.toDisplay(token.display)
            }
        } else {
            createItemView(token, itemHeight)
        }
        view.setOnClickListener { onItemSelected?.invoke(token) }
        return view
    }

    private fun obtainDivider(): View = if (dividerPool.isNotEmpty()) dividerPool.removeLast() else createDivider()

    private fun createSymbolItemView(symbol: String, itemHeight: Int): FrameLayout {
        val label = CjkCorrectedTextView(context).apply {
            text = UnicodeVariantUtils.toDisplay(symbol)
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
        val availableHeight = preferredHeight - paddingTop - paddingBottom - 2 * borderWidthPx
        if (availableHeight <= 0) return (context.dp(40)).coerceAtLeast(1)
        val count = showItemCount.coerceAtLeast(1)
        val dividers = (count - 1) * dividerHeightPx
        return ((availableHeight - dividers) / count).coerceAtLeast(1)
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
    ): FrameLayout {
        val label = CjkCorrectedTextView(context).apply {
            text = UnicodeVariantUtils.toDisplay(token.display)
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

    private fun createPressStateDrawable(): StateListDrawable {
        val pressed = sideHilitedBg ?: GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
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

    private fun createDivider(): View = View(context).apply {
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dividerHeightPx,
            )
        setBackgroundColor(sideSpacingColor)
        alpha = 0.3f
    }
}
