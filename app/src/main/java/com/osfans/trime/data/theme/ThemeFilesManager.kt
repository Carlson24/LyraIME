/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    fun listThemes(dir: File): MutableList<ThemeItem> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()

        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val item =
                    runCatching {
                        val configId = it.nameWithoutExtension
                        val cacheFile = File(DataManager.themesBuildDir, it.name)
                        val name =
                            if (cacheFile.exists()) {
                                val node = Yaml.parseToYamlNode(cacheFile.readText()).mapping
                                node?.get("name")?.string ?: return@decode null
                            } else {
                                configId.removeSuffix(".trime")
                            }
                        ThemeItem(configId, name)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode item
            }.toMutableList()
    }
}
