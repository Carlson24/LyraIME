/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.LiquidData
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LiquidKeyboardTest :
    BehaviorSpec({
        Given("liquid keyboard with mixed shorthand keys") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "keyboards": [
                        {
                          "id": "common",
                          "type": "SINGLE",
                          "name": "常用",
                          "keys": [",", ".", "?", { "text": "!", "alt_text": "！" }]
                        }
                      ]
                    }
                    """.trimIndent()
                val keyboard = Theme.json.decodeFromString<LiquidKeyboard>(json).keyboards.single()

                Then("both string and object keys are supported") {
                    keyboard.id shouldBe "common"
                    keyboard.type shouldBe LiquidData.Type.SINGLE
                    keyboard.keys shouldBe
                        listOf(
                            LiquidKeyboard.KeyItem(","),
                            LiquidKeyboard.KeyItem("."),
                            LiquidKeyboard.KeyItem("?"),
                            LiquidKeyboard.KeyItem("!", "！"),
                        )
                }
            }
        }

        Given("liquid keyboard with object-only keys") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "keyboards": [
                        {
                          "id": "math",
                          "type": "SYMBOL",
                          "name": "数学",
                          "keys": [{ "text": "≤", "alt_text": "≤" }, { "text": "≈", "alt_text": "≈" }]
                        }
                      ]
                    }
                    """.trimIndent()
                val keyboard = Theme.json.decodeFromString<LiquidKeyboard>(json).keyboards.single()

                Then("object keys keep alt_text") {
                    keyboard.keys shouldBe
                        listOf(
                            LiquidKeyboard.KeyItem("≤", "≤"),
                            LiquidKeyboard.KeyItem("≈", "≈"),
                        )
                }
            }
        }
    })
