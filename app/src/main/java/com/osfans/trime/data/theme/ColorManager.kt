// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.caverock.androidsvg.SVG
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import timber.log.Timber
import java.io.FileInputStream

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private var normalModeColor by prefs.normalModeColor
    private val followSystemDayNight by prefs.followSystemDayNight
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    private lateinit var _activeColorScheme: ColorScheme

    var activeColorScheme: ColorScheme
        get() = _activeColorScheme
        private set(value) {
            if (this::_activeColorScheme.isInitialized && _activeColorScheme == value) return
            _activeColorScheme = value
            fireChange()
        }

    private var lightModeColorScheme: ColorScheme? = null

    private var darkModeColorScheme: ColorScheme? = null

    private val BuiltinFallbackColors =
        mapOf(
            "candidate_text_color" to "text_color",
            "comment_text_color" to "candidate_text_color",
            "border_color" to "back_color",
            "candidate_separator_color" to "border_color",
            "candidate_separator_color_unrolled" to "candidate_separator_color",
            "hilited_text_color" to "text_color",
            "hilited_back_color" to "back_color",
            "hilited_candidate_text_color" to "hilited_text_color",
            "hilited_candidate_back_color" to "hilited_back_color",
            "hilited_candidate_button_color" to "hilited_candidate_back_color",
            "hilited_label_color" to "hilited_candidate_text_color",
            "hilited_comment_text_color" to "comment_text_color",
            "hilited_key_back_color" to "hilited_candidate_back_color",
            "hilited_key_text_color" to "hilited_candidate_text_color",
            "hilited_key_symbol_color" to "hilited_comment_text_color",
            "hilited_off_key_back_color" to "hilited_key_back_color",
            "hilited_on_key_back_color" to "hilited_key_back_color",
            "hilited_off_key_text_color" to "hilited_key_text_color",
            "hilited_on_key_text_color" to "hilited_key_text_color",
            "key_back_color" to "back_color",
            "key_border_color" to "border_color",
            "key_text_color" to "candidate_text_color",
            "key_symbol_color" to "comment_text_color",
            "label_color" to "candidate_text_color",
            "off_key_back_color" to "key_back_color",
            "off_key_text_color" to "key_text_color",
            "on_key_back_color" to "hilited_key_back_color",
            "on_key_text_color" to "hilited_key_text_color",
            "popup_back_color" to "key_back_color",
            "popup_text_color" to "key_text_color",
            "hilited_popup_back_color" to "hilited_key_back_color",
            "hilited_popup_text_color" to "hilited_key_text_color",
            "shadow_color" to "border_color",
            "root_background" to "back_color",
            "candidate_background" to "back_color",
            "keyboard_back_color" to "border_color",
            "keyboard_background" to "keyboard_back_color",
            "liquid_keyboard_background" to "keyboard_back_color",
            "text_back_color" to "back_color",
            "long_text_color" to "key_text_color",
            "long_text_back_color" to "key_back_color",
            "clipboard_category_back_color" to "key_back_color",
            "clipboard_category_selected_back_color" to "hilited_candidate_back_color",
            "clipboard_category_selected_text_color" to "hilited_candidate_text_color",
            "clipboard_entry_back_color" to "key_back_color",
            "hilited_clipboard_entry_back_color" to "hilited_candidate_back_color",
            "clipboard_checkbox_color" to "key_text_color",
            "candidate_border_color" to "border_color",
            "off_key_symbol_color" to "key_symbol_color",
            "on_key_symbol_color" to "hilited_key_symbol_color",
            "hilited_off_key_symbol_color" to "hilited_key_symbol_color",
            "hilited_on_key_symbol_color" to "hilited_key_symbol_color",
            "t9_side_back_color" to "key_back_color",
            "t9_side_hilited_back_color" to "hilited_key_back_color",
            "t9_side_text_color" to "key_text_color",
            "t9_side_border_color" to "key_border_color",
            "t9_side_spacing_color" to "key_border_color",
        )

    private var bitmapCache: LruCache<String, Bitmap>? = null
    private var gradientDrawableCache: LruCache<Int, GradientDrawable>? = null
    private val colorCache = LruCache<String, Int>(128)

    fun interface OnColorChangeListener {
        fun onColorChange(theme: Theme)
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onColorChange(theme) }
    }

    private fun colorScheme(id: String) = theme.colorSchemes.find { it.id == id }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activeColorScheme = evaluateActiveColorScheme()

        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxMemory / 8
        bitmapCache =
            object : LruCache<String, Bitmap>(cacheSize.toInt()) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
        gradientDrawableCache =
            object : LruCache<Int, GradientDrawable>(32) {}
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight
        colorCache.evictAll()
        activeColorScheme = evaluateActiveColorScheme()
    }

    private fun evaluateActiveColorScheme(): ColorScheme = when {
        followSystemDayNight -> {
            val defaultModeScheme = if (isNightMode) darkModeColorScheme else lightModeColorScheme

            fun resolveScheme(id: String?) = id?.let { colorScheme(it) } ?: defaultModeScheme

            colorScheme(normalModeColor)?.let { userScheme ->
                val lightSchemeId = userScheme.colors["light_scheme"]
                val darkSchemeId = userScheme.colors["dark_scheme"]

                when {
                    lightSchemeId != null && darkSchemeId != null ->
                        // 如果两者都指定了，根据当前模式选择对应的配色
                        resolveScheme(if (isNightMode) darkSchemeId else lightSchemeId)
                    lightSchemeId != null ->
                        // 如果只指定了light_scheme，说明是暗色方案
                        if (isNightMode) userScheme else resolveScheme(lightSchemeId)
                    darkSchemeId != null ->
                        // 如果只指定了dark_scheme，说明是亮色方案
                        if (isNightMode) resolveScheme(darkSchemeId) else userScheme
                    else -> defaultModeScheme
                }
            } ?: defaultModeScheme
        }
        else -> colorScheme(normalModeColor)
    } ?: colorScheme("default") ?: theme.colorSchemes.firstOrNull() ?: ColorScheme("default", emptyMap())

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(
        theme: Theme,
        suppressFireChange: Boolean = false,
    ) {
        bitmapCache?.evictAll()
        gradientDrawableCache?.evictAll()
        colorCache.evictAll()
        this.theme = theme
        val defaultScheme = colorScheme("default") ?: theme.colorSchemes.firstOrNull() ?: ColorScheme("default", emptyMap())
        lightModeColorScheme = defaultScheme.colors["light_scheme"]?.let { colorScheme(it) }
        darkModeColorScheme = defaultScheme.colors["dark_scheme"]?.let { colorScheme(it) }
        if (suppressFireChange) {
            _activeColorScheme = evaluateActiveColorScheme()
        } else {
            activeColorScheme = evaluateActiveColorScheme()
        }
    }

    fun setColorScheme(scheme: ColorScheme) {
        colorCache.evictAll()
        activeColorScheme = scheme
        normalModeColor = scheme.id
    }

    @ColorInt
    private fun resolveColor(key: String): Int = colorCache.get(key) ?: run {
        val color =
            try {
                resolveValue(key) { value ->
                    if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it, ignoreCase = true) }) {
                        throw IllegalArgumentException(
                            "Color key '$key' resolved to image file '$value' - use a color, not an image",
                        )
                    }
                    ColorUtils.parseColor(value)
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("image file") == true) throw e
                try {
                    ColorUtils.parseColor(key)
                } catch (_: IllegalArgumentException) {
                    Timber.w(e, "Color key '$key' not resolved, falling back to transparent")
                    Color.TRANSPARENT
                }
            }
        colorCache.put(key, color)
        color
    }

    private fun resolveDrawable(key: String): Drawable? {
        val drawable =
            try {
                resolveValue(key) { value ->
                    parseDrawable(value)
                }
            } catch (_: IllegalArgumentException) {
                parseDrawable(key)
            }
        return drawable
    }

    private inline fun <T> resolveValue(
        key: String,
        parser: (String) -> T,
    ): T {
        var currentKey = key

        while (true) {
            val target = activeColorScheme.colors[currentKey]
            if (!target.isNullOrEmpty()) {
                Timber.d("current: $currentKey, origin: $key, target: $target")
                return parser(target)
            }
            val fallback = theme.fallbackColors[currentKey]
            if (!fallback.isNullOrEmpty()) {
                currentKey = fallback
                continue
            }
            val altFallback = BuiltinFallbackColors[currentKey]
            if (!altFallback.isNullOrEmpty()) {
                currentKey = altFallback
            } else {
                throw IllegalArgumentException("$key not found")
            }
        }
    }

    private fun parseDrawable(value: String): Drawable? {
        if (value.isEmpty()) return null
        if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it) }) {
            val path = resolveImageFilePath(value)
            if (value.endsWith(".svg")) {
                val bitmap =
                    bitmapCache?.get(path)
                        ?: loadSvgBitmap(path)?.also {
                            bitmapCache?.put(path, it)
                        } ?: return null
                return bitmap.toDrawable(Resources.getSystem())
            }
            val bitmap =
                bitmapCache?.get(path)
                    ?: BitmapFactory.decodeFile(path)?.also {
                        bitmapCache?.put(path, it)
                    } ?: return null
            if (path.endsWith(".9.png")) {
                val chunk = bitmap.ninePatchChunk
                return if (NinePatch.isNinePatchChunk(chunk)) {
                    // for compiled nine patch image
                    NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
                } else {
                    // for source nine patch image
                    NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
                }
            }
            return bitmap.toDrawable(Resources.getSystem())
        } else {
            val color =
                try {
                    ColorUtils.parseColor(value)
                } catch (_: Exception) {
                    Color.TRANSPARENT
                }
            val cached = gradientDrawableCache?.get(color)
            return cached?.constantState?.newDrawable()
                ?: GradientDrawable().apply {
                    setColor(color)
                    gradientDrawableCache?.put(color, this)
                }
        }
    }

    private fun resolveImageFilePath(value: String): String {
        val default = DataManager.userDataBaseDir.resolve("themes/backgrounds/$backgroundFolder/$value")
        if (default.exists()) return default.absolutePath
        val fallback = DataManager.userDataBaseDir.resolve("themes/backgrounds/$value")
        return fallback.absolutePath
    }

    private fun loadSvgBitmap(path: String): Bitmap? = try {
        val svg = SVG.getFromInputStream(FileInputStream(path))
        val docWidth = svg.documentWidth
        val docHeight = svg.documentHeight
        val width = if (docWidth > 0f) docWidth.toInt() else 256
        val height = if (docHeight > 0f) docHeight.toInt() else 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        svg.renderToCanvas(canvas)
        bitmap
    } catch (e: Exception) {
        Timber.w(e, "Failed to load SVG: $path")
        null
    }

    @ColorInt
    fun getColor(key: String): Int = resolveColor(key)

    fun getDrawable(key: String): Drawable? = resolveDrawable(key)

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = when (val drawable = getDrawable(colorKey)) {
        is GradientDrawable ->
            drawable.also {
                it.cornerRadius = cornerRadius
                it.alpha = MathUtils.clamp(alpha, 0, 255)
                if (!borderColorKey.isNullOrEmpty()) {
                    try {
                        val borderColor = getColor(borderColorKey)
                        it.setStroke(borderPx, borderColor)
                    } catch (_: Exception) {
                    }
                }
            }
        else -> drawable?.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
    }

    private val SUPPORTED_IMG_FORMATS = arrayOf(".png", ".webp", ".jpg", ".gif", ".svg")
}
