// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.json") {
            val dir = File("src/test/assets")
            When("loaded") {
                val json = File(dir, "trime.json").readText()
                val generalStyle = Theme.json.decodeFromString<Theme>(json).generalStyle

                Then("it should not be null") {
                    generalStyle shouldNotBe null
                    generalStyle.autoCaps shouldBe false
                    generalStyle.candidateViewHeight shouldBe 28
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    generalStyle.enterLabel.go shouldBe "前往"
                    generalStyle.keyShadowRadius shouldBe 4f
                    generalStyle.keyShadowDirection shouldBe listOf("down", "right")
                }
            }
        }

        Given("Empty trime.json") {
            val dir = File("src/test/assets")
            When("loaded") {
                val json = File(dir, "incorrect.json").readText()
                val generalStyle = Theme.json.decodeFromString<Theme>(json).generalStyle

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe false
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.fonts.candidate shouldBe emptyList()
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"
                }
            }
        }
    })
