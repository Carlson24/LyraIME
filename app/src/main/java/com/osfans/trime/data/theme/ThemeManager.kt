/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

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

    private val keyboardJsons = mutableMapOf<String, String>()
    private val keyboardCache = mutableMapOf<String, TextKeyboard>()
    private val keyboardJsonStore = mutableMapOf<String, Map<String, String>>()
    val keyboardNames: List<String> get() = keyboardJsons.keys.toList()

    fun getKeyboard(name: String): TextKeyboard? {
        keyboardCache[name]?.let { return it }
        val json = keyboardJsons[name] ?: return null
        return Theme.json.decodeFromString<TextKeyboard>(json).also {
            keyboardCache[name] = it
        }
    }

    private fun sortJsonKeys(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.toSortedMap().mapValues { (_, v) -> sortJsonKeys(v) },
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
            if (cachedTime == lastModified) {
                keyboardJsonStore[id]?.let { store ->
                    keyboardJsons.clear()
                    keyboardJsons.putAll(store)
                    keyboardCache.clear()
                }
                return cachedTheme
            }
        }

        return loadThemeFromLua(sourceFile, id)
    }

    private fun loadThemeFromLua(file: File, id: String): Theme? {
        val cacheFile = DataManager.userThemesDir.resolve("build/$id.json")
        return try {
            val themesDir = file.parentFile!!
            val latestMtime = themesDir.walkTopDown()
                .filter { it.isFile && !it.startsWith(themesDir.resolve("build")) }
                .maxOfOrNull { it.lastModified() } ?: 0L
            val sorted = run {
                val jsonStr = if (cacheFile.exists() && cacheFile.lastModified() >= latestMtime) {
                    cacheFile.readText()
                } else {
                    LuaThemeBridge.nativeLoadTheme(file.absolutePath)
                }
                sortJsonKeys(Theme.json.parseToJsonElement(jsonStr))
            }
            val sortedObj = sorted.jsonObject

            keyboardJsons.clear()
            keyboardCache.clear()
            val keyboardsObj = sortedObj["preset_keyboards"]?.jsonObject
            if (keyboardsObj != null) {
                for ((name, kbElement) in keyboardsObj.toSortedMap()) {
                    keyboardJsons[name] = Theme.json.encodeToString(kbElement)
                }
            }
            keyboardJsonStore[id] = keyboardJsons.toMap()
            val strippedObj = JsonObject(
                sortedObj.toMutableMap().apply {
                    this["preset_keyboards"] = JsonObject(emptyMap())
                },
            )

            if (!cacheFile.exists() || cacheFile.lastModified() < latestMtime) {
                val cacheContent = Theme.json.encodeToString(sorted)
                thread(name = "theme-cache-write", priority = Thread.MIN_PRIORITY) {
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(cacheContent)
                    }
                }
            }

            Theme.json.decodeFromJsonElement<Theme>(strippedObj).also {
                themeCache[id] = file.lastModified() to it
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                cacheFile.delete()
                Timber.e(e, "Deleted stale cache for theme '$id'")
            }
            Timber.e(e, "Failed to load theme '$id'")
            if (id != "trime") {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        appContext,
                        "Failed to load theme '$id': ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            null
        }
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

    fun initNative() {
        try {
            LuaThemeBridge.nativeInit(DataManager.themesDir.absolutePath, DataManager.userThemesDir.absolutePath)
            Timber.d("nativeInit succeeded")
        } catch (e: Throwable) {
            Timber.e(e, "nativeInit FAILED")
            throw e
        }
        _activeTheme = evaluateActiveTheme()
    }

    fun init(configuration: Configuration) {
        initNative()
        ColorManager.init(configuration)
    }

    fun selectTheme(configId: String, forceReload: Boolean = false) {
        if (forceReload) {
            themeCache.remove(configId)
            keyboardJsonStore.remove(configId)
        }
        val resolvedTheme = getThemeById(configId)
        val theme = resolvedTheme.theme
        KeyActionManager.resetCache()
        keyboardCache.clear()
        FontManager.resetCache(theme)
        ColorManager.switchTheme(theme, suppressFireChange = true)
        LiquidData.init(theme)
        _activeTheme = theme
        fireChange()
        prefs.selectedTheme.setValue(resolvedTheme.configId)
    }
}
