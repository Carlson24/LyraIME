/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun computeFileSha256(file: File): String? = try {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
} catch (_: Exception) {
    null
}
