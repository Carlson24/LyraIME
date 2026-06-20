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

fun extractTarBz2(
    tarBz2File: File,
    targetDir: File,
): File {
    targetDir.mkdirs()
    execTar(listOf("xjf"), tarBz2File, targetDir)
    return singleSubDirOrSelf(targetDir)
}

fun extractTarGz(
    tarGzFile: File,
    targetDir: File,
): File {
    targetDir.mkdirs()
    execTar(listOf("xzf"), tarGzFile, targetDir)
    return singleSubDirOrSelf(targetDir)
}

fun extractTarZst(
    tarZstFile: File,
    targetDir: File,
): File {
    targetDir.mkdirs()
    execTar(listOf("--zstd", "-xf"), tarZstFile, targetDir)
    return singleSubDirOrSelf(targetDir)
}

fun extractTarGzToDir(
    inputStream: InputStream,
    targetDir: File,
): File {
    targetDir.mkdirs()
    val tmp = File.createTempFile("tar_", ".tar.gz", targetDir.parentFile)
    try {
        tmp.outputStream().use { inputStream.copyTo(it) }
        return extractTarGz(tmp, targetDir)
    } finally {
        tmp.delete()
    }
}

fun extractTarBz2ToDir(
    inputStream: InputStream,
    targetDir: File,
): File {
    targetDir.mkdirs()
    val tmp = File.createTempFile("tar_", ".tar.bz2", targetDir.parentFile)
    try {
        tmp.outputStream().use { inputStream.copyTo(it) }
        return extractTarBz2(tmp, targetDir)
    } finally {
        tmp.delete()
    }
}

fun extractTarZstToDir(
    inputStream: InputStream,
    targetDir: File,
): File {
    targetDir.mkdirs()
    val tmp = File.createTempFile("tar_", ".tar.zst", targetDir.parentFile)
    try {
        tmp.outputStream().use { inputStream.copyTo(it) }
        return extractTarZst(tmp, targetDir)
    } finally {
        tmp.delete()
    }
}

private fun execTar(flags: List<String>, file: File, targetDir: File) {
    val cmd = mutableListOf("tar")
    cmd.addAll(flags)
    cmd.addAll(listOf(file.absolutePath, "-C", targetDir.absolutePath, "--strip-components=1"))
    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val stderr = process.inputStream.bufferedReader().use { it.readText() }
        throw RuntimeException("tar extraction failed (exit $exitCode): $stderr")
    }
}

private fun singleSubDirOrSelf(targetDir: File): File {
    val subFiles = targetDir.listFiles()
    return if (subFiles != null && subFiles.size == 1 && subFiles[0].isDirectory) {
        subFiles[0]
    } else {
        targetDir
    }
}

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

fun extractZipToDir(
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

    return singleSubDirOrSelf(targetDir)
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
