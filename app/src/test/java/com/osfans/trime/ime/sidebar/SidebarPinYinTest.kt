// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.sidebar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class SidebarPinYinTest :
    StringSpec({

        "layouts parse from key names" {
            SidebarLayout.fromKey("t9") shouldBe SidebarLayout.T9
            SidebarLayout.fromKey("14") shouldBe SidebarLayout.KEY14
            SidebarLayout.fromKey("18") shouldBe SidebarLayout.KEY18
            SidebarLayout.fromKey("t9_zrm") shouldBe SidebarLayout.T9_ZRM
            SidebarLayout.fromKey("t9_flypy") shouldBe SidebarLayout.T9_FLYPY
            SidebarLayout.fromKey("14_zrm") shouldBe SidebarLayout.KEY14_ZRM
            SidebarLayout.fromKey("14_flypy") shouldBe SidebarLayout.KEY14_FLYPY
            SidebarLayout.fromKey("18_zrm") shouldBe SidebarLayout.KEY18_ZRM
            SidebarLayout.fromKey("18_flypy") shouldBe SidebarLayout.KEY18_FLYPY
            SidebarLayout.fromKey("unknown") shouldBe SidebarLayout.T9
            SidebarLayout.fromKey(null) shouldBe SidebarLayout.T9
        }

        "t9 layout maps letters to digit keys" {
            SidebarLayout.T9.keyArray['a'.code] shouldBe '2'
            SidebarLayout.T9.keyArray['h'.code] shouldBe '4'
            SidebarLayout.T9.keyArray['s'.code] shouldBe '7'
            SidebarLayout.T9.keyArray['z'.code] shouldBe '9'
        }

        "key14 layout maps letters to keys" {
            SidebarLayout.KEY14.keyArray['w'.code] shouldBe 'q'
            SidebarLayout.KEY14.keyArray['n'.code] shouldBe 'b'
            SidebarLayout.KEY14.keyArray['i'.code] shouldBe 'u'
        }

        "key18 layout maps letters to keys" {
            SidebarLayout.KEY18.keyArray['e'.code] shouldBe 'w'
            SidebarLayout.KEY18.keyArray['o'.code] shouldBe 'i'
            SidebarLayout.KEY18.keyArray['n'.code] shouldBe 'b'
        }

        "t9 full pinyin expands digit sequence" {
            val candidates = SidebarPinYin.possibleCombinations("2", SidebarLayout.T9)
            candidates shouldContain "a"
            candidates shouldContain "b"
            candidates shouldContain "c"

            val twoKeys = SidebarPinYin.possibleCombinations("26", SidebarLayout.T9)
            twoKeys shouldContain "an"
            twoKeys shouldContain "bo"
        }

        "key14 full pinyin expands letter sequence" {
            val candidates = SidebarPinYin.possibleCombinations("gab", SidebarLayout.KEY14)
            candidates shouldContain "han"

            val li = SidebarPinYin.possibleCombinations("lu", SidebarLayout.KEY14)
            li shouldContain "li"
        }

        "key18 full pinyin expands letter sequence" {
            val candidates = SidebarPinYin.possibleCombinations("hab", SidebarLayout.KEY18)
            candidates shouldContain "han"
        }

        "t9_zrm double pinyin" {
            val liu = SidebarPinYin.possibleCombinations("57", SidebarLayout.T9_ZRM)
            liu shouldContain "liu"

            val zhan = SidebarPinYin.possibleCombinations("85", SidebarLayout.T9_ZRM)
            zhan shouldContain "zhan"

            val an = SidebarPinYin.possibleCombinations("26", SidebarLayout.T9_ZRM)
            an shouldContain "an"
        }

        "t9_flypy double pinyin" {
            val liu = SidebarPinYin.possibleCombinations("57", SidebarLayout.T9_FLYPY)
            liu shouldContain "liu"

            val shuang = SidebarPinYin.possibleCombinations("85", SidebarLayout.T9_FLYPY)
            shuang shouldContain "shuang"
        }

        "key14_zrm double pinyin" {
            val shuang = SidebarPinYin.possibleCombinations("ud", SidebarLayout.KEY14_ZRM)
            shuang shouldContain "shuang"
        }

        "key14_flypy double pinyin" {
            val yue = SidebarPinYin.possibleCombinations("yt", SidebarLayout.KEY14_FLYPY)
            yue shouldContain "yue"
        }

        "key18_zrm double pinyin" {
            val zhuan = SidebarPinYin.possibleCombinations("vr", SidebarLayout.KEY18_ZRM)
            zhuan shouldContain "zhuan"
        }

        "key18_flypy double pinyin" {
            val xiong = SidebarPinYin.possibleCombinations("xs", SidebarLayout.KEY18_FLYPY)
            xiong shouldContain "xiong"

            val er = SidebarPinYin.possibleCombinations("er", SidebarLayout.KEY18_FLYPY)
            er shouldContain "er"
        }

        "physical code of pinyin matches the sequence used to select it" {
            SidebarLayout.T9.physicalCodeOf("han") shouldBe "426"
            SidebarLayout.KEY14.physicalCodeOf("han") shouldBe "gab"
            SidebarLayout.KEY18.physicalCodeOf("han") shouldBe "hab"
            SidebarLayout.T9_ZRM.physicalCodeOf("liu") shouldBe "57"
            SidebarLayout.T9_ZRM.physicalCodeOf("a") shouldBe "22"
            SidebarLayout.KEY14_FLYPY.physicalCodeOf("yue") shouldBe "tt"
            SidebarLayout.KEY18_FLYPY.physicalCodeOf("shuang") shouldBe "ul"
        }

        "every pinyin syllable maps to a key code in each layout" {
            val nonSyllables =
                setOf(
                    "b", "c", "d", "f", "g", "h", "j", "k", "l", "m",
                    "n", "p", "q", "r", "s", "t", "w", "x", "y", "z",
                    "i", "u", "v",
                )
            for (layout in SidebarLayout.entries) {
                val expected =
                    if (layout.scheme == PinyinScheme.FULL) {
                        SidebarPinYin.allPinyin
                    } else {
                        SidebarPinYin.allPinyin - nonSyllables
                    }
                val covered = layout.pinyinMap.values.flatten().toSet()
                covered shouldBe expected
            }
        }
    })
