/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.WeakHashSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val bundledThemes = ThemeFilesManager.listThemes(DataManager.themesDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userThemesDir)
        return bundledThemes + userThemes
    }

    private lateinit var _activeTheme: Theme

    var activeTheme: Theme
        get() {
            if (!::_activeTheme.isInitialized) {
                _activeTheme = evaluateActiveTheme()
            }
            return _activeTheme
        }
        private set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
    )

    private val themeCache = mutableMapOf<String, Pair<Long, Theme>>()

    private val prettyJson = Json(from = Theme.json) {
        prettyPrint = true
        prettyPrintIndent = "\t"
    }

    private fun sortJsonKeys(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.toSortedMap().mapValues { (_, v) -> sortJsonKeys(v) }
        )
        is JsonArray -> JsonArray(element.map { sortJsonKeys(it) })
        else -> element
    }

    private fun loadThemeByIdOrNull(id: String): Theme? {
        val bundledSource = DataManager.themesDir.resolve("$id.lua")
        val userSource = DataManager.userThemesDir.resolve("$id.lua")
        val sourceFile = if (userSource.exists()) userSource else bundledSource

        if (!sourceFile.exists()) {
            Timber.w("Theme source file not found for '$id'")
            return null
        }

        val lastModified = sourceFile.lastModified()
        themeCache[id]?.let { (cachedTime, cachedTheme) ->
            if (cachedTime == lastModified) return cachedTheme
        }

        return loadThemeFromLua(sourceFile, id)
    }

    private fun loadThemeFromLua(file: File, id: String): Theme? = try {
        val cacheFile = DataManager.userThemesDir.resolve("build/$id.json")
        val jsonStr = if (cacheFile.exists() && cacheFile.lastModified() >= file.lastModified()) {
            cacheFile.readText()
        } else {
            val json = LuaThemeBridge.nativeLoadTheme(file.absolutePath)
            cacheFile.parentFile?.mkdirs()
            val sorted = sortJsonKeys(prettyJson.parseToJsonElement(json))
            val formatted = prettyJson.encodeToString(sorted)
            cacheFile.writeText(formatted)
            json
        }
        Theme.json.decodeFromString<Theme>(jsonStr).also {
            themeCache[id] = file.lastModified() to it
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to load theme '$id'")
        null
    }

    private fun getThemeById(id: String): ResolvedTheme {
        loadThemeByIdOrNull(id)?.let { return ResolvedTheme(id, it) }

        if (id != "trime") {
            loadThemeByIdOrNull("trime")?.let {
                Timber.w("Theme '$id' is unavailable, fallback to default theme 'trime'")
                return ResolvedTheme("trime", it)
            }
        }

        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            loadThemeByIdOrNull(fallbackId)?.let {
                Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                return ResolvedTheme(fallbackId, it)
            }
        }

        Timber.e("All themes failed to load, using hardcoded fallback")
        return ResolvedTheme("fallback", createFallbackTheme())
    }

    private fun createFallbackTheme(): Theme = Theme(
        name = "Fallback",
        generalStyle = GeneralStyle(),
    )

    private fun evaluateActiveTheme(): Theme {
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme.setValue(resolvedTheme.configId)
        }
        KeyActionManager.resetCache()
        FontManager.resetCache(newTheme)
        ColorManager.switchTheme(newTheme, suppressFireChange = true)
        LiquidData.init(newTheme)
        return newTheme
    }

    fun init(configuration: Configuration) {
        LuaThemeBridge.nativeInit(DataManager.themesDir.absolutePath)
        _activeTheme = evaluateActiveTheme()
        ColorManager.init(configuration)
    }

    fun selectTheme(configId: String, forceReload: Boolean = false) {
        if (forceReload) {
            themeCache.remove(configId)
        }
        val resolvedTheme = getThemeById(configId)
        val theme = resolvedTheme.theme
        KeyActionManager.resetCache()
        FontManager.resetCache(theme)
        ColorManager.switchTheme(theme, suppressFireChange = true)
        LiquidData.init(theme)
        _activeTheme = theme
        fireChange()
        prefs.selectedTheme.setValue(resolvedTheme.configId)
    }
}
