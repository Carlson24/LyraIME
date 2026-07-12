/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 LyraIME Contributors
 */
package com.osfans.trime.ui.texteditor

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.MarkedYAMLException

object YamlSyntaxChecker {

    private val safeYaml = Yaml(SafeConstructor(LoaderOptions()))

    data class YamlError(
        val line: Int,
        val column: Int,
        val message: String,
    )

    fun firstError(text: String): YamlError? = try {
        safeYaml.loadAll(text).forEach { }
        null
    } catch (e: MarkedYAMLException) {
        val mark = e.problemMark
        if (mark != null) {
            YamlError(
                line = mark.line + 1,
                column = mark.column + 1,
                message = e.problem ?: "YAML syntax error",
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    fun check(text: String): DiagnosticsContainer? {
        val container = DiagnosticsContainer()
        var errors = 0
        try {
            for (obj in safeYaml.loadAll(text)) {
            }
        } catch (e: MarkedYAMLException) {
            val mark = e.problemMark ?: return null
            val flatIndex = lineColumnToIndex(text, mark.line, mark.column)
            val detail = DiagnosticDetail(
                briefMessage = e.problem ?: "YAML syntax error",
                detailedMessage = e.toString(),
            )
            container.addDiagnostic(
                DiagnosticRegion(flatIndex, flatIndex + 1, DiagnosticRegion.SEVERITY_ERROR, 0, detail),
            )
            errors++
        } catch (_: Exception) {
            return null
        }
        return if (errors > 0) container else DiagnosticsContainer()
    }

    fun hasErrors(text: String): Boolean = firstError(text) != null

    private fun lineColumnToIndex(text: String, line: Int, column: Int): Int {
        var currentLine = 0
        var index = 0
        for ((i, ch) in text.withIndex()) {
            if (currentLine == line) {
                return (index + column).coerceAtMost(text.length)
            }
            if (ch == '\n') {
                currentLine++
                index = i + 1
            }
        }
        return index
    }
}
