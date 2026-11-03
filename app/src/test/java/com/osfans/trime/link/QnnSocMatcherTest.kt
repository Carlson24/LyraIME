/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class QnnSocMatcherTest :
    StringSpec({
        "normalizes SoC model suffix variants to the base model" {
            QnnSocMatcher.normalize("SM8850P") shouldBe "SM8850"
            QnnSocMatcher.normalize("sm8850p") shouldBe "SM8850"
            QnnSocMatcher.normalize("SM8850L") shouldBe "SM8850"
            QnnSocMatcher.normalize("SM8850-AC") shouldBe "SM8850"
            QnnSocMatcher.normalize("SM8850-1-AB") shouldBe "SM8850"
            QnnSocMatcher.normalize("SM8750P") shouldBe "SM8750"
            QnnSocMatcher.normalize("SM8475") shouldBe "SM8475"
        }

        "rejects unknown or empty SoC model values" {
            QnnSocMatcher.normalize(null) shouldBe null
            QnnSocMatcher.normalize("") shouldBe null
            QnnSocMatcher.normalize("unknown") shouldBe null
        }

        "does not over-match a longer numeric model" {
            QnnSocMatcher.normalize("SM88501") shouldBe "SM88501"
            QnnSocMatcher.isSupported("v81", "SM88501") shouldBe false
        }

        "accepts known SoC suffix variants for the packaged QNN variant" {
            QnnSocMatcher.isSupported("v81", "SM8850P") shouldBe true
            QnnSocMatcher.isSupported("v81", "sm8850p") shouldBe true
            QnnSocMatcher.isSupported("v81", "SM8850") shouldBe true
            QnnSocMatcher.isSupported("v81", "SM8850-AC") shouldBe true
            QnnSocMatcher.isSupported("v81", "SM8850-1-AB") shouldBe true
            QnnSocMatcher.isSupported("v79", "SM8750P") shouldBe true
            QnnSocMatcher.isSupported("v73", "SM8550-AC") shouldBe true
        }

        "rejects SoCs from a different QNN variant" {
            QnnSocMatcher.isSupported("v81", "SM8750") shouldBe false
            QnnSocMatcher.isSupported("v79", "SM8850") shouldBe false
            QnnSocMatcher.isSupported("v81", "unknown") shouldBe false
            QnnSocMatcher.isSupported("v81", "") shouldBe false
        }

        "rejects devices with no configured QNN variant" {
            QnnSocMatcher.isSupported(null, "SM8850P") shouldBe false
            QnnSocMatcher.isSupported("", "SM8850P") shouldBe false
        }
    })
