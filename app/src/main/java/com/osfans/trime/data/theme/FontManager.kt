// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.UnicodeVariantUtils
import timber.log.Timber
import java.io.File

object FontManager {
    private lateinit var theme: Theme

    private enum class FontKey {
        HANB_FONT,
        LATIN_FONT,
        CANDIDATE_FONT,
        CLIPBOARD_FONT,
        CLIPBOARD_CATEGORY_FONT,
        COMMENT_FONT,
        KEY_FONT,
        LABEL_FONT,
        POPUP_FONT,
        SYMBOL_FONT,
        HINT_FONT,
        TEXT_FONT,
        LONG_TEXT_FONT,
        TOOLBAR_FONT,
        SIDEBAR_FONT,
        CANDIDATES_TOOL_FONT,
        CANDIDATES_TOOL_POPUP_FONT,
    }

    private val fontDir get() = File(DataManager.userDataBaseDir, "themes/fonts")
    private val fontDirFallback get() = File(DataManager.userDataBaseDir, "fonts")
    lateinit var hanBFont: Typeface
        private set
    lateinit var latinFont: Typeface
        private set
    var fontFeatureSettings: String = ""
        private set

    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    fun resetCache(theme: Theme) {
        typefaceCache.clear()
        this.theme = theme
        fontFeatureSettings = theme.generalStyle.fonts.variations
            .filter { (key, value) -> value && key != "cpct" }
            .keys.joinToString(", ")
        UnicodeVariantUtils.configure(
            variants = theme.generalStyle.fonts.display,
            enabled = theme.generalStyle.fonts.variations["cpct"] == true,
        )
        hanBFont = getTypeface(FontKey.HANB_FONT.name)
        latinFont = getTypeface(FontKey.LATIN_FONT.name)
    }

    @JvmStatic
    fun getTypeface(key: String): Typeface {
        if (typefaceCache.containsKey(key)) {
            return typefaceCache[key]!!
        }
        Timber.d("getTypeface() key=%s", key)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fontFamilies = mutableListOf<FontFamily>()
            getFontFamilies(key).let {
                if (it.isEmpty()) {
                    typefaceCache[key] = Typeface.DEFAULT
                    return@getTypeface Typeface.DEFAULT
                }
                fontFamilies.addAll(getFontFamilies(FontKey.LATIN_FONT.name))
                fontFamilies.addAll(it)
                fontFamilies.addAll(getFontFamilies(FontKey.HANB_FONT.name))
            }
            buildTypeface(fontFamilies).let {
                typefaceCache[key] = it
                return@getTypeface it
            }
        }
        getTypefaceOrDefault(key).let {
            typefaceCache[key] = it
            return@getTypeface it
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildTypeface(fontFamilies: List<FontFamily>): Typeface {
        if (fontFamilies.isEmpty()) return Typeface.DEFAULT
        val builder = Typeface.CustomFallbackBuilder(fontFamilies[0])
        for (i in 1 until fontFamilies.size) {
            builder.addCustomFallback(fontFamilies[i])
        }
        return builder.setSystemFallback("sans-serif").build()
    }

    private fun getTypefaceOrDefault(key: String): Typeface {
        val fonts = getFontFromStyle(key)

        fun handler(fontName: String): Typeface? {
            val fontFile = File(fontDir, fontName)
            if (fontFile.exists()) {
                return Typeface.createFromFile(fontFile)
            }
            val fallbackFile = File(fontDirFallback, fontName)
            if (fallbackFile.exists()) {
                return Typeface.createFromFile(fallbackFile)
            }
            Timber.w("font %s not found", fontFile)
            return null
        }

        return fonts?.firstNotNullOfOrNull { handler(it) } ?: Typeface.DEFAULT
    }

    private fun getFontFromStyle(key: String): List<String>? {
        val style = theme.generalStyle
        return when (FontKey.entries.firstOrNull { it.name == key.uppercase() }) {
            FontKey.HANB_FONT -> style.fonts.hanb
            FontKey.LATIN_FONT -> style.fonts.latin
            FontKey.CANDIDATE_FONT -> style.fonts.candidate
            FontKey.CLIPBOARD_FONT -> style.fonts.clipboard.ifEmpty { style.fonts.key }
            FontKey.CLIPBOARD_CATEGORY_FONT -> style.fonts.clipboard_category.ifEmpty { style.fonts.key }
            FontKey.COMMENT_FONT -> style.fonts.comment
            FontKey.KEY_FONT -> style.fonts.key
            FontKey.LABEL_FONT -> style.fonts.label
            FontKey.POPUP_FONT -> style.fonts.popup
            FontKey.SYMBOL_FONT -> style.fonts.symbol
            FontKey.HINT_FONT -> style.fonts.hint
            FontKey.TEXT_FONT -> style.fonts.text
            FontKey.TOOLBAR_FONT -> theme.toolBar.buttonFont
            FontKey.SIDEBAR_FONT -> style.fonts.sidebar
            FontKey.CANDIDATES_TOOL_FONT -> theme.candidatesTool?.buttonFont.orEmpty()
            FontKey.CANDIDATES_TOOL_POPUP_FONT -> theme.candidatesTool?.popupFont.orEmpty()
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamily(fontName: String): FontFamily? {
        if (fontFamilyCache.containsKey(fontName)) {
            return fontFamilyCache[fontName]!!
        }
        val fontFile = File(fontDir, fontName)
        if (fontFile.exists()) {
            return FontFamily.Builder(Font.Builder(fontFile).build()).build()
        }
        val fallbackFile = File(fontDirFallback, fontName)
        if (fallbackFile.exists()) {
            return FontFamily.Builder(Font.Builder(fallbackFile).build()).build()
        }
        Timber.w("font %s not found", fontFile)
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamilies(key: String): List<FontFamily> {
        val fonts = getFontFromStyle(key)

        return fonts?.mapNotNull { getFontFamily(it) } ?: listOf()
    }
}
