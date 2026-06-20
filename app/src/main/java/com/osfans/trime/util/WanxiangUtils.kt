/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.osfans.trime.data.base.DataManager
import java.io.File

fun compareVersions(a: String, b: String): Int {
    fun parse(v: String): List<Int> = v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val va = parse(a)
    val vb = parse(b)
    for (i in 0 until maxOf(va.size, vb.size)) {
        val aa = va.getOrElse(i) { 0 }
        val bb = vb.getOrElse(i) { 0 }
        if (aa != bb) return aa.compareTo(bb)
    }
    return 0
}

fun readLocalWanxiangVersion(): String = try {
    val userDataFileLua = File(DataManager.userDataDir, "lua/wanxiang/wanxiang.lua")
    val userDataFile = File(DataManager.userDataDir, "lua/wanxiang.lua")
    val file = when {
        userDataFileLua.exists() -> userDataFileLua
        userDataFile.exists() -> userDataFile
        else -> null
    }
    if (file != null) {
        val content = file.readText()
        Regex("""wanxiang\.version\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1) ?: "v0.0.0"
    } else {
        "v0.0.0"
    }
} catch (_: Exception) {
    "v0.0.0"
}
