/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString

class PresetKeyTest :
    BehaviorSpec({
        Given("a theme with plain string preset labels") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "name": "t",
                      "style": {},
                      "preset_keys": {
                        "BackSpace": { "label": "⌫", "send": "BackSpace" },
                        "Mode": { "label": "中", "ascii_label": "En", "toggle": "ascii_mode" }
                      }
                    }
                    """.trimIndent()
                val keys = Theme.json.decodeFromString<Theme>(json).presetKeys

                Then("string labels map to a single plain segment") {
                    keys["BackSpace"]?.label shouldBe listOf(TextKeyboard.LabelSegment(text = "⌫"))
                    keys["BackSpace"]?.asciiLabel shouldBe emptyList()
                }

                Then("ascii_label is mapped from snake_case") {
                    keys["Mode"]?.label shouldBe listOf(TextKeyboard.LabelSegment(text = "中"))
                    keys["Mode"]?.asciiLabel shouldBe listOf(TextKeyboard.LabelSegment(text = "En"))
                }
            }
        }

        Given("a theme with LabelSpec object labels") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "name": "t",
                      "style": {},
                      "preset_keys": {
                        "Shift": {
                          "label": { "text": ["⇧", "Shift"], "color": ["red", "blue"], "align": ["left", "right"] },
                          "send": "Shift_L"
                        },
                        "Return": {
                          "label": { "text": "↩", "bold": true, "scale": 0.8 },
                          "send": "Return"
                        }
                      }
                    }
                    """.trimIndent()
                val keys = Theme.json.decodeFromString<Theme>(json).presetKeys

                Then("parallel arrays expand into styled segments") {
                    keys["Shift"]?.label shouldBe
                        listOf(
                            TextKeyboard.LabelSegment(text = "⇧", color = "red", align = TextKeyboard.Align.LEFT),
                            TextKeyboard.LabelSegment(text = "Shift", color = "blue", align = TextKeyboard.Align.RIGHT),
                        )
                }

                Then("single-value styles apply to all segments") {
                    keys["Return"]?.label shouldBe
                        listOf(TextKeyboard.LabelSegment(text = "↩", bold = true, scale = 0.8f))
                }
            }
        }

        Given("a theme with a LabelSpec segment array") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "name": "t",
                      "style": {},
                      "preset_keys": {
                        "Escape": {
                          "label": [ { "text": "A", "valign": "top" }, { "text": "1" } ],
                          "send": "Escape"
                        }
                      }
                    }
                    """.trimIndent()
                val key = Theme.json.decodeFromString<Theme>(json).presetKeys.getValue("Escape")

                Then("segment array keeps per-segment styles") {
                    key.label shouldBe
                        listOf(
                            TextKeyboard.LabelSegment(text = "A", valign = TextKeyboard.VerticalAlign.TOP),
                            TextKeyboard.LabelSegment(text = "1"),
                        )
                }
            }
        }

        Given("an empty string label") {
            When("decoded from JSON") {
                val json =
                    """
                    {
                      "name": "t",
                      "style": {},
                      "preset_keys": {
                        "Space": { "label": "", "send": "space" }
                      }
                    }
                    """.trimIndent()
                val key = Theme.json.decodeFromString<Theme>(json).presetKeys.getValue("Space")

                Then("empty string becomes no label") {
                    key.label shouldBe emptyList()
                }
            }
        }

        Given("a rich preset label") {
            When("encoded and decoded back") {
                val presetKey =
                    PresetKey(label = listOf(TextKeyboard.LabelSegment(text = "⌫", color = "red")))
                val encoded = Theme.json.encodeToString(PresetKey.serializer(), presetKey)
                val roundTripped = Theme.json.decodeFromString(PresetKey.serializer(), encoded)

                Then("styles survive round-trip") {
                    roundTripped.label shouldBe listOf(TextKeyboard.LabelSegment(text = "⌫", color = "red"))
                }
            }
        }

        Given("a plain preset label") {
            When("encoded and decoded back") {
                val presetKey = PresetKey(label = listOf(TextKeyboard.LabelSegment(text = "↩")))
                val encoded = Theme.json.encodeToString(PresetKey.serializer(), presetKey)
                val roundTripped = Theme.json.decodeFromString(PresetKey.serializer(), encoded)

                Then("plain label round-trips as a single segment") {
                    roundTripped.label shouldBe listOf(TextKeyboard.LabelSegment(text = "↩"))
                }
            }
        }
    })
