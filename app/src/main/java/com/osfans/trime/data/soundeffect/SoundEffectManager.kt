/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.soundeffect

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.LuaThemeBridge
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.util.FileUtils
import timber.log.Timber
import java.io.File

object SoundEffectManager {

    private val userDir: File
        get() {
            val dest = File(DataManager.userDataBaseDir, "themes/soundeffect")
            val old = File(DataManager.userDataBaseDir, "sound")
            return FileUtils.rename(old, dest.name).getOrDefault(dest.also { it.mkdirs() })
        }

    private val userDirFallback get() = File(DataManager.userDataBaseDir, "soundeffect")

    private fun listSounds(): MutableList<SoundEffect> {
        fun list(dir: File) = dir.listFiles { f -> f.name.endsWith(".sound.lua") }.orEmpty().toList()
        val files = list(userDir) + list(userDirFallback)
        return files
            .mapNotNull decode@{ f ->
                val effect = try {
                    val json = LuaThemeBridge.nativeLoadSoundEffect(f.absolutePath)
                    val result = com.osfans.trime.data.theme.Theme.json.decodeFromString<SoundEffect>(json)
                    if (result.name.isEmpty()) {
                        result.copy(name = f.nameWithoutExtension.removeSuffix(".sound"))
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to decode sound effect descriptor '${f.absolutePath}'")
                    null
                }
                return@decode effect
            }.toMutableList()
    }

    private fun getEffect(name: String) = userEffects.find { it.name == name }

    private val userEffects: MutableList<SoundEffect> get() = listSounds()

    private var soundEffectPref by AppPrefs.defaultInstance().keyboard.customSoundEffect

    fun switchEffect(name: String) {
        val effect = getEffect(name)
        if (effect == null) {
            Timber.w("Unknown sound effect '$name'")
            return
        }
        activeSoundEffect = effect
        soundEffectPref = name
        InputFeedbackManager.reloadSoundEffects()
    }

    fun init() {
        activeSoundEffect = getEffect(soundEffectPref) ?: return
    }

    var activeSoundEffect: SoundEffect? = null
        private set

    val activeAudioPaths: List<String>
        get() {
            return activeSoundEffect?.let { e ->
                val subPath = e.folder
                e.sound.mapNotNull { name ->
                    val primary = userDir.resolve(subPath).resolve(name)
                    if (primary.exists()) {
                        primary.path
                    } else {
                        val fallback = userDirFallback.resolve(subPath).resolve(name)
                        if (fallback.exists()) fallback.path else primary.path
                    }
                }
            } ?: listOf()
        }

    fun getAllSoundEffects(): List<SoundEffect> = userEffects
}
