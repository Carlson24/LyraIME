package com.osfans.trime.util

object UnicodeVariantUtils {
    private var enabled = false
    private var map: Map<String, String> = emptyMap()

    fun configure(variants: Map<String, String>, enabled: Boolean) {
        this.map = variants
        this.enabled = enabled
    }

    fun toDisplay(text: String): String {
        if (!enabled || map.isEmpty() || text.isEmpty()) return text
        return buildString {
            text.codePoints().forEach { cp ->
                val ch = String(Character.toChars(cp))
                append(map[ch] ?: ch)
            }
        }
    }
}
