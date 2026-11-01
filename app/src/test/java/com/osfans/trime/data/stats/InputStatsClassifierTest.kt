/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputStatsClassifierTest :
    StringSpec({
        "counts only Han characters" {
            InputStatsClassifier.countHan("abc你好123，。！") shouldBe 2
        }

        "ignores text without Han characters" {
            InputStatsClassifier.classify("abc123，。！😀") shouldBe null
        }

        "classifies a single character" {
            val sample = InputStatsClassifier.classify("你")
            sample?.bucket shouldBe 1
            sample?.hanCount shouldBe 1
        }

        "classifies two characters" {
            val sample = InputStatsClassifier.classify("你好")
            sample?.bucket shouldBe 2
            sample?.hanCount shouldBe 2
        }

        "classifies three characters" {
            InputStatsClassifier.classify("你好啊")?.bucket shouldBe 3
        }

        "classifies four characters" {
            InputStatsClassifier.classify("你好世界")?.bucket shouldBe 4
        }

        "classifies more than four characters" {
            val sample = InputStatsClassifier.classify("你好世界啊")
            sample?.bucket shouldBe 5
            sample?.hanCount shouldBe 5
        }

        "ignores punctuation mixed into Han text" {
            val sample = InputStatsClassifier.classify("你好，世界！")
            sample?.bucket shouldBe 4
            sample?.hanCount shouldBe 4
        }

        "counts supplementary Han characters as single code points" {
            val supplementaryHan = "\uD840\uDC00"
            InputStatsClassifier.countHan(supplementaryHan) shouldBe 1
            InputStatsClassifier.classify(supplementaryHan)?.bucket shouldBe 1
        }
    })
