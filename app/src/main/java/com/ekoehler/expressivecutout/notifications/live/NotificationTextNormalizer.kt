package com.ekoehler.expressivecutout.notifications.live

import java.text.Normalizer
import java.util.Locale

/** Normalizes notification text for deterministic case-, accent-, and punctuation-insensitive matching. */
object NotificationTextNormalizer {
    private val combiningMarks = Regex("\\p{M}+")
    private val punctuation = Regex("[^\\p{L}\\p{N}%]+")
    private val whitespace = Regex("\\s+")

    /** Returns a lowercase ASCII-accent-folded token string while retaining percent signs. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()
    }
}
