// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PreeditOffsetTest :
    BehaviorSpec({
        // U+26B99, a CJK extension B ideograph: two UTF-16 code units but one code point.
        val extB = "\uD85A\uDF99"

        Given("a preedit made of a single supplementary-plane character") {
            Then("offsets between the surrogates snap back to the pair start") {
                extB.alignToCodePointBoundary(0) shouldBe 0
                extB.alignToCodePointBoundary(1) shouldBe 0
                extB.alignToCodePointBoundary(2) shouldBe 2
            }

            Then("out-of-range offsets are clamped") {
                extB.alignToCodePointBoundary(-1) shouldBe 0
                extB.alignToCodePointBoundary(3) shouldBe 2
            }
        }

        Given("a preedit ending with a supplementary-plane character") {
            val text = "O$extB"

            Then("the end of the text stays a valid span boundary") {
                text.alignToCodePointBoundary(3) shouldBe 3
            }

            Then("an offset inside the trailing surrogate pair snaps back") {
                text.alignToCodePointBoundary(2) shouldBe 1
            }
        }

        Given("a preedit starting with a supplementary-plane character") {
            val text = "${extB}a"

            Then("an offset inside the leading surrogate pair snaps back") {
                text.alignToCodePointBoundary(1) shouldBe 0
            }

            Then("offsets after the pair are preserved") {
                text.alignToCodePointBoundary(2) shouldBe 2
                text.alignToCodePointBoundary(3) shouldBe 3
            }
        }

        Given("a preedit with an emoji") {
            val text = "hi\uD83D\uDE42"

            Then("only offsets inside the surrogate pair are adjusted") {
                text.alignToCodePointBoundary(2) shouldBe 2
                text.alignToCodePointBoundary(3) shouldBe 2
                text.alignToCodePointBoundary(4) shouldBe 4
            }
        }

        Given("a preedit made of BMP characters only") {
            Then("offsets are left untouched") {
                "ni'hao".alignToCodePointBoundary(0) shouldBe 0
                "ni'hao".alignToCodePointBoundary(3) shouldBe 3
                "ni'hao".alignToCodePointBoundary(6) shouldBe 6
            }
        }
    })
