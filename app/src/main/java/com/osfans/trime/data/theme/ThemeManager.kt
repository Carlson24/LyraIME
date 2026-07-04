/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import timber.log.Timber
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val bundledThemes = ThemeFilesManager.listThemes(DataManager.themesDir)
        val userThemesDir = File(DataManager.userDataBaseDir, "themes")
        val userThemes = if (userThemesDir.exists()) ThemeFilesManager.listThemes(userThemesDir) else mutableListOf()
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

    private fun loadThemeByIdOrNull(id: String): Theme? {
        val bundledSource = DataManager.themesDir.resolve("$id.yaml")
        val userSource = File(DataManager.userDataBaseDir, "themes/$id.yaml")
        val sourceFile = if (userSource.exists()) userSource else bundledSource
        val cacheFile = File(DataManager.themesBuildDir, "$id.yaml")

        val sourceNewest =
            sourceFile.parentFile
                ?.listFiles()
                ?.maxOfOrNull { it.lastModified() }
                ?: 0L

        val cacheValid =
            cacheFile.exists() &&
                sourceFile.exists() &&
                cacheFile.lastModified() >= sourceNewest

        if (!cacheValid) {
            if (!sourceFile.exists()) {
                Timber.w("Theme source file not found for '$id'")
                return null
            }

            if (userSource.exists()) {
                val userThemesDir = File(DataManager.userDataBaseDir, "themes")
                val toDelete = mutableListOf<File>()
                val toRestore = mutableMapOf<File, File>()
                try {
                    userThemesDir.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".yaml") || f.name.endsWith(".yml")) {
                            val dest = File(DataManager.userDataDir, f.name)
                            if (!dest.exists()) {
                                f.copyTo(dest)
                                toDelete.add(dest)
                            } else {
                                val backup = File(DataManager.userDataDir, "${f.name}.theme_backup")
                                dest.copyTo(backup, overwrite = true)
                                f.copyTo(dest, overwrite = true)
                                toRestore[backup] = dest
                            }
                        }
                    }
                    Rime.deployRimeConfigFile(id, "config_version")
                    val compiled = File(DataManager.resolveDeployedResourcePath(id))
                    if (compiled.exists()) {
                        compiled.copyTo(cacheFile, overwrite = true)
                    }
                } finally {
                    toDelete.forEach { it.delete() }
                    toRestore.forEach { (backup, original) ->
                        backup.copyTo(original, overwrite = true)
                        backup.delete()
                    }
                }
            } else {
                val tmp = File(DataManager.sharedDataDir, "$id.yaml")
                try {
                    sourceFile.copyTo(tmp, overwrite = true)
                    if (!Rime.deployRimeConfigFile(id, "config_version")) {
                        Timber.w("Failed to deploy theme config file '$id.yaml'")
                        return null
                    }
                    val compiled = File(DataManager.resolveDeployedResourcePath(id))
                    if (compiled.exists()) {
                        compiled.copyTo(cacheFile, overwrite = true)
                    }
                } finally {
                    tmp.delete()
                }
            }
        }

        if (!cacheFile.exists()) {
            Timber.w("Theme cache file not found for '$id'")
            return null
        }

        val lastModified = cacheFile.lastModified()
        themeCache[id]?.let { (cachedTime, cachedTheme) ->
            if (cachedTime == lastModified) return cachedTheme
        }
        return try {
            val node = Yaml.parseToYamlNode(cacheFile.readText())
            val mapping = node.mapping
            if (mapping == null) {
                Timber.w("Failed to load theme '$id': YAML root is not a mapping")
                null
            } else {
                Theme.decode(mapping).also { themeCache[id] = cacheFile.lastModified() to it }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load theme '$id'")
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

        error("No valid theme available")
    }

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
        _activeTheme = evaluateActiveTheme()
        ColorManager.init(configuration)
    }

    fun selectTheme(configId: String) {
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
