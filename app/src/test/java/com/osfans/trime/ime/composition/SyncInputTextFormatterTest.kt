/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncInputTextFormatterTest :
    StringSpec({
        "keeps short text and maps offsets" {
            val snapshot =
                TargetTextSnapshot(
                    text = "hello world",
                    startOffset = 0,
                    selectionStart = 5,
                    selectionEnd = 5,
                    editable = true,
                )

            val display = SyncInputTextFormatter.format(snapshot)

            display.text shouldBe "hello world"
            display.cursor shouldBe 5
            display.displayOffsetToDocument(0) shouldBe 0
            display.displayOffsetToDocument(11) shouldBe 11
            display.documentOffsetToDisplay(5) shouldBe 5
        }

        "adds an ellipsis for partial extracted text" {
            val snapshot =
                TargetTextSnapshot(
                    text = "abc",
                    startOffset = 100,
                    selectionStart = 1,
                    selectionEnd = 1,
                    editable = true,
                    truncatedBefore = true,
                )

            val display = SyncInputTextFormatter.format(snapshot)

            display.text shouldBe "…abc"
            display.documentStartOffset shouldBe 100
            display.cursor shouldBe 2
            display.displayOffsetToDocument(0) shouldBe 100
            display.documentOffsetToDisplay(101) shouldBe 2
        }

        "keeps the cursor visible in very long text" {
            val prefix = "a".repeat(30_000)
            val suffix = "b".repeat(30_000)
            val cursor = prefix.length + 3
            val snapshot =
                TargetTextSnapshot(
                    text = prefix + "xyz" + suffix,
                    startOffset = 0,
                    selectionStart = cursor,
                    selectionEnd = cursor,
                    editable = true,
                )

            val display = SyncInputTextFormatter.format(snapshot)

            display.truncatedBefore shouldBe true
            display.truncatedAfter shouldBe true
            display.text.startsWith("…") shouldBe true
            display.text.endsWith("…") shouldBe true
            display.cursor shouldBe 5_001
            display.displayOffsetToDocument(display.cursor!!) shouldBe cursor
        }

        "maps a selection range" {
            val snapshot =
                TargetTextSnapshot(
                    text = "abcdef",
                    startOffset = 0,
                    selectionStart = 1,
                    selectionEnd = 4,
                    editable = true,
                )

            val display = SyncInputTextFormatter.format(snapshot)

            display.selectionStart shouldBe 1
            display.selectionEnd shouldBe 4
            display.cursor shouldBe 4
            display.displayOffsetToDocument(display.selectionStart!!) shouldBe 1
            display.displayOffsetToDocument(display.selectionEnd!!) shouldBe 4
        }

        "keeps line breaks on one line without shifting offsets" {
            val snapshot =
                TargetTextSnapshot(
                    text = "a\r\nb\tc\nd",
                    startOffset = 0,
                    selectionStart = 6,
                    selectionEnd = 6,
                    editable = true,
                )

            val display = SyncInputTextFormatter.format(snapshot)

            display.text shouldBe "a\u200B\u21B5b c\u21B5d"
            display.text.length shouldBe snapshot.text.length
            display.cursor shouldBe 6
            display.displayOffsetToDocument(6) shouldBe 6
            display.documentOffsetToDisplay(6) shouldBe 6
        }
    })
