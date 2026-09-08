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
import timber.log.Timber
import java.io.File

object FontManager {
    private lateinit var theme: Theme

    private val fontDir get() = File(DataManager.userDataBaseDir, "themes/fonts")
    private val fontDirFallback get() = File(DataManager.userDataBaseDir, "fonts")

    private var typeface: Typeface? = null
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    fun resetCache(theme: Theme) {
        typeface = null
        fontFamilyCache.clear()
        this.theme = theme
    }

    @JvmStatic
    fun getTypeface(): Typeface {
        typeface?.let { return it }
        Timber.d("getTypeface()")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fontFamilies = getFontFamilies()
            if (fontFamilies.isEmpty()) {
                typeface = Typeface.DEFAULT
                return typeface!!
            }
            buildTypeface(fontFamilies).let {
                typeface = it
                return it
            }
        }
        getTypefaceOrDefault().let {
            typeface = it
            return it
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

    private fun getTypefaceOrDefault(): Typeface = getFontFromStyle()
        .firstNotNullOfOrNull { handler(it) } ?: Typeface.DEFAULT

    private fun handler(fontName: String): Typeface? {
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

    private fun getFontFromStyle(): List<String> = theme.generalStyle.fonts.font_list

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
    private fun getFontFamilies(): List<FontFamily> = getFontFromStyle().mapNotNull { getFontFamily(it) }
}
