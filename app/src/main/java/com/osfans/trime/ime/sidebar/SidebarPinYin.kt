package com.osfans.trime.ime.sidebar

private val t9KeyMap =
    mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz",
    )

private val key14Map =
    mapOf(
        'q' to "qw",
        'e' to "er",
        't' to "ty",
        'u' to "ui",
        'o' to "op",
        'a' to "as",
        'd' to "df",
        'g' to "gh",
        'j' to "jk",
        'l' to "l",
        'z' to "zx",
        'c' to "cv",
        'b' to "bn",
        'm' to "m",
    )

private val key18Map =
    mapOf(
        'q' to "q",
        'w' to "we",
        'r' to "rt",
        'y' to "y",
        'u' to "u",
        'i' to "io",
        'p' to "p",
        'a' to "a",
        's' to "sd",
        'f' to "fg",
        'h' to "h",
        'j' to "jk",
        'l' to "l",
        'z' to "z",
        'x' to "xc",
        'v' to "v",
        'b' to "bn",
        'm' to "m",
    )

private val singleInitials = "bpmfdtnlgkhjqxrzcsyw"

private val specialInitials =
    mapOf(
        "zh" to 'v',
        "ch" to 'i',
        "sh" to 'u',
    )

private val zrmFinalMap =
    mapOf(
        "a" to 'a',
        "o" to 'o',
        "e" to 'e',
        "i" to 'i',
        "u" to 'u',
        "v" to 'v',
        "ai" to 'l',
        "ei" to 'z',
        "ao" to 'k',
        "ou" to 'b',
        "an" to 'j',
        "en" to 'f',
        "ang" to 'h',
        "eng" to 'g',
        "ong" to 's',
        "ia" to 'w',
        "ie" to 'x',
        "iao" to 'c',
        "iu" to 'q',
        "ian" to 'm',
        "in" to 'n',
        "iang" to 'd',
        "ing" to 'y',
        "iong" to 's',
        "ua" to 'w',
        "uo" to 'o',
        "uai" to 'y',
        "ui" to 'v',
        "uan" to 'r',
        "un" to 'p',
        "uang" to 'd',
        "ue" to 't',
        "ve" to 't',
        "ueng" to 'g',
    )

private val flypyFinalMap =
    mapOf(
        "a" to 'a',
        "o" to 'o',
        "e" to 'e',
        "i" to 'i',
        "u" to 'u',
        "v" to 'v',
        "ai" to 'd',
        "ei" to 'w',
        "ao" to 'c',
        "ou" to 'z',
        "an" to 'j',
        "en" to 'f',
        "ang" to 'h',
        "eng" to 'g',
        "ong" to 's',
        "ia" to 'x',
        "ie" to 'p',
        "iao" to 'n',
        "iu" to 'q',
        "ian" to 'm',
        "in" to 'b',
        "iang" to 'l',
        "ing" to 'k',
        "iong" to 's',
        "ua" to 'x',
        "uo" to 'o',
        "uai" to 'k',
        "ui" to 'v',
        "uan" to 'r',
        "un" to 'y',
        "uang" to 'l',
        "ue" to 't',
        "ve" to 't',
        "ueng" to 'g',
    )

private val zeroInitialMap =
    mapOf(
        "a" to "aa",
        "ai" to "ai",
        "an" to "an",
        "ao" to "ao",
        "e" to "ee",
        "ei" to "ei",
        "en" to "en",
        "er" to "er",
        "o" to "oo",
        "ou" to "ou",
        "ang" to "ah",
        "eng" to "eg",
    )

private val zrmZeroInitialAlt =
    mapOf(
        "ai" to "al",
        "ao" to "ak",
        "ei" to "ez",
        "ou" to "ob",
    )

private val flypyZeroInitialAlt =
    mapOf(
        "ai" to "ad",
        "ao" to "ac",
        "ei" to "ew",
        "ou" to "oz",
    )

private val yFinalMap =
    mapOf(
        "ya" to "ia",
        "yan" to "ian",
        "yang" to "iang",
        "yao" to "iao",
        "ye" to "ie",
        "yi" to "i",
        "yin" to "in",
        "ying" to "ing",
        "yo" to "o",
        "yong" to "iong",
        "you" to "iu",
        "yu" to "v",
        "yue" to "ue",
        "yuan" to "uan",
        "yun" to "un",
    )

private val wFinalMap =
    mapOf(
        "wu" to "u",
        "wa" to "ua",
        "wo" to "uo",
        "wai" to "uai",
        "wei" to "ui",
        "wan" to "uan",
        "wen" to "un",
        "wang" to "uang",
        "weng" to "eng",
    )

enum class PinyinScheme {
    FULL,
    ZRM,
    FLYPY,
}

enum class SidebarLayout(
    val keyName: String,
    val scheme: PinyinScheme,
    private val keyMap: Map<Char, String>,
) {
    T9("t9", PinyinScheme.FULL, t9KeyMap),
    KEY14("14", PinyinScheme.FULL, key14Map),
    KEY18("18", PinyinScheme.FULL, key18Map),
    T9_ZRM("t9_zrm", PinyinScheme.ZRM, t9KeyMap),
    T9_FLYPY("t9_flypy", PinyinScheme.FLYPY, t9KeyMap),
    KEY14_ZRM("14_zrm", PinyinScheme.ZRM, key14Map),
    KEY14_FLYPY("14_flypy", PinyinScheme.FLYPY, key14Map),
    KEY18_ZRM("18_zrm", PinyinScheme.ZRM, key18Map),
    KEY18_FLYPY("18_flypy", PinyinScheme.FLYPY, key18Map),
    ;

    val isT9Style: Boolean
        get() = keyMap === t9KeyMap

    val keyArray: CharArray by lazy {
        CharArray(128) { idx -> idx.toChar() }.also { arr ->
            for ((key, letters) in keyMap) {
                for (c in letters) {
                    arr[c.code] = key
                }
            }
        }
    }

    val pinyinMap: Map<String, List<String>> by lazy {
        buildMap<String, MutableList<String>> {
            for (pinyin in SidebarPinYin.allPinyin) {
                for (code in SidebarPinYin.keyCodesOf(pinyin, this@SidebarLayout)) {
                    val mapped = String(code.map { keyArray[it.code] }.toCharArray())
                    getOrPut(mapped) { mutableListOf() }.add(pinyin)
                }
            }
        }
    }

    fun isValidKeyChar(c: Char): Boolean = c in keyMap || c == '\''

    fun physicalCodeOf(pinyin: String): String? = SidebarPinYin.keyCodeOf(pinyin, this)?.let { code ->
        String(code.map { keyArray[it.code] }.toCharArray())
    }

    fun codeMatchingPhysical(pinyin: String, physical: String): String? =
        SidebarPinYin.keyCodesOf(pinyin, this).firstOrNull { code ->
            String(code.map { keyArray[it.code] }.toCharArray()) == physical
        }

    companion object {
        fun fromKey(name: String?): SidebarLayout = entries.firstOrNull { it.keyName == name } ?: T9
    }
}

object SidebarPinYin {
    val allPinyin =
        setOf(
            "a", "ai", "an", "ang", "ao",
            "b", "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi",
            "bian", "biao", "bie", "bin", "bing", "bo", "bu", "biang",
            "c", "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng",
            "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi",
            "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo",
            "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
            "d", "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng",
            "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du",
            "duan", "dui", "dun", "duo",
            "e", "ei", "en", "eng", "er",
            "f", "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "g", "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng",
            "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "h", "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng",
            "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "i",
            "j", "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing",
            "jiong", "jiu", "ju", "juan", "jue", "jun",
            "k", "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng",
            "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "l", "la", "lai", "lan", "lang", "lao", "le", "lei", "leng",
            "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling",
            "liu", "long", "lou", "lu", "luan", "lun", "luo", "lv", "lo", "lve",
            "m", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng",
            "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "n", "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng",
            "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu",
            "nong", "nou", "nu", "nuan", "nuo", "nv", "nve",
            "o", "ou",
            "p", "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng",
            "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
            "q", "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing",
            "qiong", "qiu", "qu", "quan", "que", "qun",
            "r", "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong",
            "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
            "s", "sa", "sai", "san", "sang", "sao", "se", "sen", "seng",
            "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi",
            "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo",
            "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
            "t", "ta", "tai", "tan", "tang", "tao", "te", "teng",
            "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu",
            "tuan", "tui", "tun", "tuo",
            "u", "v",
            "w", "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "x", "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing",
            "xiong", "xiu", "xu", "xuan", "xue", "xun",
            "y", "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying",
            "yo", "yong", "you", "yu", "yuan", "yue", "yun",
            "z", "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng",
            "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhen", "zheng", "zhi",
            "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo",
            "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo",
        )

    internal fun keyCodeOf(pinyin: String, layout: SidebarLayout): String? = when (layout.scheme) {
        PinyinScheme.FULL -> pinyin
        PinyinScheme.ZRM, PinyinScheme.FLYPY -> doublePinyinCode(pinyin, layout.scheme)
    }

    internal fun keyCodesOf(pinyin: String, layout: SidebarLayout): List<String> {
        if (layout.scheme == PinyinScheme.FULL) return listOf(pinyin)
        val primary = doublePinyinCode(pinyin, layout.scheme) ?: return emptyList()
        val alt =
            when (layout.scheme) {
                PinyinScheme.ZRM -> zrmZeroInitialAlt[pinyin]
                PinyinScheme.FLYPY -> flypyZeroInitialAlt[pinyin]
            }
        return if (alt != null && alt != primary) listOf(primary, alt) else listOf(primary)
    }

    private fun doublePinyinCode(pinyin: String, scheme: PinyinScheme): String? {
        zeroInitialMap[pinyin]?.let { return it }

        for ((initial, key) in specialInitials) {
            if (pinyin.startsWith(initial)) {
                val rest = pinyin.removePrefix(initial)
                val finalKey = finalKey(rest, initial.last(), scheme) ?: return null
                return "$key$finalKey"
            }
        }

        if (pinyin.startsWith("y")) {
            val rest = yFinalMap[pinyin] ?: return null
            val finalKey = finalKey(rest, 'y', scheme) ?: return null
            return "y$finalKey"
        }

        if (pinyin.startsWith("w")) {
            val rest = wFinalMap[pinyin] ?: return null
            val finalKey = finalKey(rest, 'w', scheme) ?: return null
            return "w$finalKey"
        }

        val initial = pinyin.firstOrNull() ?: return null
        if (initial !in singleInitials) return null
        val rest = pinyin.drop(1)
        if (rest.isEmpty()) return null
        val finalKey = finalKey(rest, initial, scheme) ?: return null
        return "$initial$finalKey"
    }

    private fun finalKey(rest: String, initial: Char, scheme: PinyinScheme): Char? {
        val finalMap =
            when (scheme) {
                PinyinScheme.ZRM -> zrmFinalMap
                PinyinScheme.FLYPY -> flypyFinalMap
                else -> return null
            }
        if (rest == "u" && initial in "jqxy") return 'v'
        return finalMap[rest]
    }

    private val searchCaches = HashMap<SidebarLayout, LinkedHashMap<String, List<String>>>()

    fun possibleCombinations(sequence: String?, layout: SidebarLayout): List<String> {
        if (sequence.isNullOrBlank()) return emptyList()

        val cache =
            searchCaches.getOrPut(layout) {
                object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean = size > 32
                }
            }
        cache[sequence]?.let { return it }

        val len = minOf(sequence.length, 6)
        val numChars = CharArray(len)
        for (i in 0 until len) {
            val c = sequence[i]
            numChars[i] = if (c.code < 128) layout.keyArray[c.code] else c
        }
        val numString = String(numChars)

        val result = mutableListOf<String>()
        for (length in numString.length downTo 1) {
            layout.pinyinMap[numString.substring(0, length)]?.let { result.addAll(it) }
        }

        cache[sequence] = result
        return result
    }
}
