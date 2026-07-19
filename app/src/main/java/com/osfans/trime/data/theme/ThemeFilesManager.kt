/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    private val namePattern = Regex("""name\s*=\s*"([^"]*)"""")

    fun listThemes(dir: File): MutableList<ThemeItem> {
        val files = dir.listFiles { _, name -> name.endsWith(".lua") } ?: return mutableListOf()

        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                val configId = file.nameWithoutExtension
                val name = runCatching {
                    val text = file.readText()
                    namePattern.find(text)?.groupValues?.getOrNull(1)
                        ?: configId
                }.getOrElse { e ->
                    Timber.w("Failed to read theme name from ${file.absolutePath}: ${e.message}")
                    configId
                }
                ThemeItem(configId, name)
            }
            .toMutableList()
    }
}
