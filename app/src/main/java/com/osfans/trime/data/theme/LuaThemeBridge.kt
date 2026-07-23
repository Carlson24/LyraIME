/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

object LuaThemeBridge {
    init {
        System.loadLibrary("lua_theme_jni")
    }

    @JvmStatic
    external fun nativeInit(themesDir: String, userThemesDir: String)

    @JvmStatic
    external fun nativeLoadTheme(path: String): String

    @JvmStatic
    external fun nativeLoadSoundEffect(path: String): String

    @JvmStatic
    external fun nativeParseYaml(path: String, keyPath: String): String

    @JvmStatic
    external fun nativeDestroy()
}
