package org.fossify.messages.helpers

import java.util.concurrent.ConcurrentHashMap

/**
 * Stable, cached colours (and the 2x2 label) for the text avatars shown in the lists.
 *
 * The colour is derived from the display name only, so two rows that resolve to the same name
 * - even when they belong to different thread ids - always get the exact same colour. The
 * result is memoised, so binding a long list does not recompute (or re-hash) anything, per the
 * 1.0.3 spec.
 */
object AvatarColors {
    /** Material-ish palette used for the text avatars, mirroring the reference screenshots. */
    private val palette = intArrayOf(
        0xffef5350.toInt(),
        0xffab47bc.toInt(),
        0xff5c6bc0.toInt(),
        0xff29b6f6.toInt(),
        0xff26a69a.toInt(),
        0xff66bb6a.toInt(),
        0xffffa726.toInt(),
        0xff8d6e63.toInt()
    )

    private val cache = ConcurrentHashMap<String, Int>()

    /** Cached background colour for [name]. */
    fun colorFor(name: String): Int = cache.getOrPut(name) {
        val index = (name.hashCode() % palette.size + palette.size) % palette.size
        palette[index]
    }

    /**
     * Lays the avatar text out 2 characters per line, as agreed: 1-2 characters stay on one
     * line, 3-4 characters are split over two lines (2x2 grid).
     */
    fun textFor(name: String): String {
        val chars = name.trim().take(4)
        return if (chars.length <= 2) {
            chars
        } else {
            "${chars.substring(0, 2)}\n${chars.substring(2)}"
        }
    }

    /**
     * True when [text] looks like an actual name instead of a bare phone number, so a
     * conversation with no matched contact gets the neutral avatar rather than one reading
     * "+861".
     */
    fun isRealName(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }

        val withoutNumberChars = text.filterNot {
            it.isWhitespace() || it == '-' || it == '+' || it == '(' || it == ')'
        }
        return withoutNumberChars.isNotEmpty() && !withoutNumberChars.all { it.isDigit() }
    }
}
