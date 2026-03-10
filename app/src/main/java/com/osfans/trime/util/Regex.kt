// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

fun String.removeRegexSet(regexSet: Set<Regex>): String {
    regexSet.forEach { replace(it, String.EMPTY) }
    return this
}

fun String.matchesAny(regexSet: Set<Regex>): Boolean = regexSet.any { it.matches(this) }

fun String.extractAll(regexSet: Set<Regex>): List<String> {
    val results = mutableSetOf<String>()
    regexSet.forEach { regex ->
        regex.findAll(this).forEach { matchResult ->
            if (matchResult.groupValues.size > 1) {
                for (i in 1 until matchResult.groupValues.size) {
                    val group = matchResult.groupValues[i]
                    if (group.isNotEmpty()) {
                        results.add(group)
                    }
                }
            } else {
                results.add(matchResult.value)
            }
        }
    }
    return results.toList()
}
