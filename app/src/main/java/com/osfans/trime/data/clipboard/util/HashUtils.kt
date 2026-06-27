// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {
    private val SHA256 = "SHA-256"
    private const val FILE_READ_BUFFER = 1024 * 1024

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance(SHA256)
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toUppercaseHex()
    }

    fun sha256Hex(input: ByteArray): String {
        val md = MessageDigest.getInstance(SHA256)
        val bytes = md.digest(input)
        return bytes.toUppercaseHex()
    }

    fun sha256HexLower(input: String): String {
        val md = MessageDigest.getInstance(SHA256)
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun hashContent(data: ByteArray, offset: Int = 0, len: Int = data.size - offset): ByteArray {
        val md = MessageDigest.getInstance(SHA256)
        md.update(data, offset, len)
        return md.digest()
    }

    fun calculateTextHash(text: String): String {
        if (text.isEmpty()) return ""
        return sha256Hex(text)
    }

    fun calculateFileHash(file: File): String? = try {
        val md = MessageDigest.getInstance(SHA256)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(FILE_READ_BUFFER)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        md.digest().toUppercaseHex()
    } catch (e: Exception) {
        null
    }

    fun calculateFileProfileHash(fileName: String, fileHash: String): String = sha256Hex("$fileName|$fileHash")

    fun calculateGroupHash(entries: List<GroupEntry>): String {
        val sorted = entries.sortedWith(compareBy(Utf8Comparator) { it.relativePath })
        val sb = StringBuilder()
        for (entry in sorted) {
            if (entry.isDirectory) {
                sb.append("D|${entry.relativePath}\u0000")
            } else {
                sb.append("F|${entry.relativePath}|${entry.length}|${entry.contentHash}\u0000")
            }
        }
        return sha256Hex(sb.toString())
    }

    data class GroupEntry(
        val relativePath: String,
        val isDirectory: Boolean,
        val length: Long = 0,
        val contentHash: String = "",
    )

    private fun ByteArray.toUppercaseHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    private object Utf8Comparator : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val bytesA = a.toByteArray(Charsets.UTF_8)
            val bytesB = b.toByteArray(Charsets.UTF_8)
            val minLen = minOf(bytesA.size, bytesB.size)
            for (i in 0 until minLen) {
                val diff = (bytesA[i].toInt() and 0xFF) - (bytesB[i].toInt() and 0xFF)
                if (diff != 0) return diff
            }
            return bytesA.size - bytesB.size
        }
    }
}
