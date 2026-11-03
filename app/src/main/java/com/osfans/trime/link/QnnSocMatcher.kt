/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import java.util.Locale

/**
 * Matches the device SoC against the SoCs supported by a packaged QNN variant.
 *
 * `Build.SOC_MODEL` is not guaranteed to contain only the base part number. Some
 * devices report variant suffixes such as `SM8850P`, `SM8850L`, `SM8850-AC` or
 * `SM8850-1-AB`, all of which share the same HTP arch as `SM8850`. Normalizing
 * the suffix away before comparing against the whitelist keeps those devices on
 * QNN instead of incorrectly falling back to CPU.
 */
internal object QnnSocMatcher {
    /**
     * SoCs whose Hexagon DSP arch matches each packaged QNN variant. The HTP
     * backend is variant-specific: loading e.g. the v81 Skel on a v73 device makes
     * QNN initialization fail (or abort in the native layer), so we only attempt
     * QNN when the device SoC is in the whitelist for the configured variant.
     *
     * Entries are base models; suffix variants are handled by [normalize].
     */
    private val variantSocWhitelist =
        mapOf(
            "v81" to setOf("SM8850"),
            "v79" to setOf("SM8750"),
            "v75" to setOf("SM8650"),
            "v73" to setOf("SM8550"),
            "v69" to setOf("SM8450", "SM8475"),
            "v68" to setOf("SM8350"),
            "v66" to setOf("SM8250"),
        )

    private val socBasePattern = Regex("^([A-Z]+\\d+)")

    private const val UNKNOWN_SOC = "UNKNOWN"

    /**
     * Extracts the base SoC model, dropping SKU/variant suffixes such as the `P`
     * in `SM8850P` or the `-AC` in `SM8850-AC`.
     */
    fun normalize(raw: String?): String? {
        val upper = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (upper.isEmpty() || upper == UNKNOWN_SOC) return null
        return socBasePattern.find(upper)?.groupValues?.get(1)
    }

    /** True only when [rawSoc] matches a SoC supported by [variant]. */
    fun isSupported(
        variant: String?,
        rawSoc: String?,
    ): Boolean {
        val soc = normalize(rawSoc) ?: return false
        val whitelist = variantSocWhitelist[variant] ?: return false
        return soc in whitelist
    }
}
