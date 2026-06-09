/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

fun extractZip(
    zipFile: File,
    targetDir: File,
) {
    targetDir.mkdirs()

    val zip = ZipFile(zipFile)
    val entries = zip.entries().asSequence().toList()
    val prefix = findCommonZipPrefix(entries.map { it.name.trim('/') })

    for (entry in entries) {
        val name = entry.name.trim('/')
        var relativePath = name
        if (prefix != null && name.startsWith(prefix)) {
            relativePath = name.removePrefix(prefix).trim('/')
        }
        if (relativePath.isEmpty()) continue

        val destFile = File(targetDir, relativePath)
        if (entry.isDirectory) {
            destFile.mkdirs()
        } else {
            destFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    zip.close()
}

fun extractZipToTempDir(
    inputStream: InputStream,
    targetDir: File,
): File {
    targetDir.mkdirs()

    ZipInputStream(inputStream).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val f = File(targetDir, entry.name)
            if (entry.isDirectory) {
                f.mkdirs()
            } else {
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { zis.copyTo(it) }
            }
            entry = zis.nextEntry
        }
    }

    val subFiles = targetDir.listFiles()
    return if (subFiles != null && subFiles.size == 1 && subFiles[0].isDirectory) {
        subFiles[0]
    } else {
        targetDir
    }
}

private fun findCommonZipPrefix(entries: List<String>): String? {
    if (entries.size <= 1) return null
    val first = entries.first()
    val slashIndex = first.indexOf('/')
    if (slashIndex < 0) return null
    val candidatePrefix = first.substring(0, slashIndex + 1)
    if (entries.all { it == candidatePrefix.trim('/') || it.startsWith(candidatePrefix) }) {
        return candidatePrefix
    }
    return null
}
