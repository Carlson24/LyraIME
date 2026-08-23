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
            "b", "ba", "bai", "ban", "bang", "bao", "be", "beh", "bei", "ben",
            "beng", "bi", "bia", "biai", "bian", "biang", "biao", "bie", "biee", "bin",
            "bing", "bio", "biong", "biu", "bo", "bong", "bou", "bu",
            "c", "ca", "cai", "can", "cang", "cao", "ce", "ceh", "cei", "cen",
            "ceng", "cha", "chai", "chan", "chang", "chao", "che", "cheh", "chei", "chen",
            "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chuao",
            "chue", "chuee", "chui", "chun", "chung", "chuo", "chuong", "chuou", "ci", "cia",
            "ciai", "cian", "ciang", "ciao", "cie", "ciee", "cii", "cin", "cing", "cio",
            "ciong", "ciu", "cong", "cou", "cu", "cua", "cuai", "cuan", "cuang", "cue",
            "cui", "cun", "cung", "cuo", "cv", "cva", "cvai", "cvan", "cvang", "cve",
            "cvi", "cvn", "cvng", "cvo",
            "d", "da", "dai", "dan", "dang", "dao", "de", "deh", "dei", "den",
            "deng", "di", "dia", "diai", "dian", "diang", "diao", "die", "diee", "din",
            "ding", "dio", "diong", "diu", "do", "dong", "dou", "du", "dua", "duai",
            "duan", "duang", "duao", "due", "duee", "dui", "dun", "dung", "duo", "duong",
            "duou", "dv", "dva", "dvai", "dvan", "dvang", "dve", "dvi", "dvn", "dvng",
            "dvo",
            "e", "eh", "ei", "en", "eng", "er",
            "f", "fa", "fai", "fan", "fang", "fao", "fe", "feh", "fei", "fen",
            "feng", "fiao", "fo", "fong", "fou", "fu", "fua", "fuai", "fuan", "fuang",
            "fue", "fui", "fun", "fung", "fuo",
            "g", "ga", "gai", "gan", "gang", "gao", "ge", "geh", "gei", "gen",
            "geng", "go", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "guao",
            "gue", "guee", "gui", "gun", "gung", "guo", "guong", "guou",
            "h", "ha", "hai", "han", "hang", "hao", "he", "heh", "hei", "hen",
            "heng", "ho", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "huao",
            "hue", "huee", "hui", "hun", "hung", "huo", "huong", "huou",
            "i",
            "j", "ji", "jia", "jiai", "jian", "jiang", "jiao", "jie", "jiee", "jin",
            "jing", "jio", "jiong", "jiu", "jv", "jva", "jvai", "jvan", "jvang", "jve",
            "jvi", "jvn", "jvng", "jvo",
            "k", "ka", "kai", "kan", "kang", "kao", "ke", "keh", "kei", "ken",
            "keng", "ko", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kuao",
            "kue", "kuee", "kui", "kun", "kung", "kuo", "kuong", "kuou",
            "l", "la", "lai", "lan", "lang", "lao", "le", "leh", "lei", "len",
            "leng", "li", "lia", "liai", "lian", "liang", "liao", "lie", "liee", "lin",
            "ling", "lio", "liong", "liu", "lo", "long", "lou", "lu", "lua", "luai",
            "luan", "luang", "luao", "lue", "luee", "lui", "lun", "lung", "luo", "luong",
            "luou", "lv", "lva", "lvai", "lvan", "lvang", "lve", "lvi", "lvn", "lvng",
            "lvo",
            "m", "ma", "mai", "man", "mang", "mao", "me", "meh", "mei", "men",
            "meng", "mi", "mia", "miai", "mian", "miang", "miao", "mie", "miee", "min",
            "ming", "mio", "miong", "miu", "mo", "mong", "mou", "mu",
            "n", "na", "nai", "nan", "nang", "nao", "ne", "neh", "nei", "nen",
            "neng", "ni", "nia", "niai", "nian", "niang", "niao", "nie", "niee", "nin",
            "ning", "nio", "niong", "niu", "no", "nong", "nou", "nu", "nua", "nuai",
            "nuan", "nuang", "nuao", "nue", "nuee", "nui", "nun", "nung", "nuo", "nuong",
            "nuou", "nv", "nva", "nvai", "nvan", "nvang", "nve", "nvi", "nvn", "nvng",
            "nvo",
            "o", "ong", "ou",
            "p", "pa", "pai", "pan", "pang", "pao", "pe", "peh", "pei", "pen",
            "peng", "pi", "pia", "piai", "pian", "piang", "piao", "pie", "piee", "pin",
            "ping", "pio", "piong", "piu", "po", "pong", "pou", "pu",
            "q", "qi", "qia", "qiai", "qian", "qiang", "qiao", "qie", "qiee", "qin",
            "qing", "qio", "qiong", "qiu", "qv", "qva", "qvai", "qvan", "qvang", "qve",
            "qvi", "qvn", "qvng", "qvo",
            "r", "ra", "rai", "ran", "rang", "rao", "re", "reh", "rei", "ren",
            "reng", "ri", "rong", "rou", "ru", "rua", "ruai", "ruan", "ruang", "ruao",
            "rue", "ruee", "rui", "run", "rung", "ruo", "ruong", "ruou",
            "s", "sa", "sai", "san", "sang", "sao", "se", "seh", "sei", "sen",
            "seng", "sha", "shai", "shan", "shang", "shao", "she", "sheh", "shei", "shen",
            "sheng", "shi", "shong", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shuao",
            "shue", "shuee", "shui", "shun", "shung", "shuo", "shuong", "shuou", "si", "sia",
            "siai", "sian", "siang", "siao", "sie", "siee", "sii", "sin", "sing", "sio",
            "siong", "siu", "song", "sou", "su", "sua", "suai", "suan", "suang", "sue",
            "sui", "sun", "sung", "suo", "sv", "sva", "svai", "svan", "svang", "sve",
            "svi", "svn", "svng", "svo",
            "t", "ta", "tai", "tan", "tang", "tao", "te", "teh", "tei", "ten",
            "teng", "ti", "tia", "tiai", "tian", "tiang", "tiao", "tie", "tiee", "tin",
            "ting", "tio", "tiong", "tiu", "to", "tong", "tou", "tu", "tua", "tuai",
            "tuan", "tuang", "tuao", "tue", "tuee", "tui", "tun", "tung", "tuo", "tuong",
            "tuou", "tv", "tva", "tvai", "tvan", "tvang", "tve", "tvi", "tvn", "tvng",
            "tvo",
            "u",
            "v",
            "w", "wa", "wai", "wan", "wang", "wao", "we", "wee", "wei", "wen",
            "weng", "wo", "wong", "wou", "wu",
            "x", "xi", "xia", "xiai", "xian", "xiang", "xiao", "xie", "xiee", "xin",
            "xing", "xio", "xiong", "xiu", "xv", "xva", "xvai", "xvan", "xvang", "xve",
            "xvi", "xvn", "xvng", "xvo",
            "y", "ya", "yai", "yan", "yang", "yao", "ye", "yee", "yei", "yi",
            "yin", "ying", "yo", "yong", "you", "yu", "yuai", "yuan", "yuang", "yue",
            "yui", "yun", "yung", "yuo",
            "z", "za", "zai", "zan", "zang", "zao", "ze", "zeh", "zei", "zen",
            "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zheh", "zhei", "zhen",
            "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhuao",
            "zhue", "zhuee", "zhui", "zhun", "zhung", "zhuo", "zhuong", "zhuou", "zi", "zia",
            "ziai", "zian", "ziang", "ziao", "zie", "ziee", "zii", "zin", "zing", "zio",
            "ziong", "ziu", "zong", "zou", "zu", "zua", "zuai", "zuan", "zuang", "zue",
            "zui", "zun", "zung", "zuo", "zv", "zva", "zvai", "zvan", "zvang", "zve",
            "zvi", "zvn", "zvng", "zvo",
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

    internal fun displayPinyin(pinyin: String): String {
        if ('v' !in pinyin) return pinyin
        return when (pinyin.first()) {
            in "jqxy" -> pinyin.replace('v', 'u')
            in "nlzcs" -> pinyin.replace('v', 'ü')
            else -> pinyin
        }
    }
}
