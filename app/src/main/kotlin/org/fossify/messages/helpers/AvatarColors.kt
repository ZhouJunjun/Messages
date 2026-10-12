package org.fossify.messages.helpers

import java.util.concurrent.ConcurrentHashMap

/**
 * Stable, cached colours (and the 2x2 label) for the text avatars shown in the lists.
 *
 * The colour is derived from the display name only, so two rows that resolve to the same name
 * - even when they belong to different thread ids - always get the exact same colour. The
 * result is memoised, so binding a long list does not recompute (or re-hash) anything, per the
 * 1.0.3 spec.
 *
 * The palette started as the Material 400 set with grey mixed in (1.1.03 spec). Since then it is
 * driven directly by the user, so the slot order below is just "the current list", not a hue
 * wheel: 1.1.07 dropped the lime slot and asked for a red, a cyan-blue and a yellow that are
 * brighter than the greyed-down set was.
 *
 * Every entry is picked as a *background*: the label on top of it is always pure white
 * (`Color.WHITE`, set by both adapters), so each colour has to stay dark enough for the white
 * glyphs to sit on it. Contrast ratios against #FFFFFF (WCAG relative-luminance formula) are
 * listed per line; the original palette solved for >= 3.8:1, and the hand-picked 1.1.07 colours
 * land lower (2.1 - 3.8) because they are deliberately lighter - the yellowish ones sit near the
 * bottom of that band by nature, since "yellow" and "white text" pull in opposite directions.
 * The yellow slot is a compromise: a mustard/olive tint (#9c8c22, 3.39) was tried first but read
 * as dull dung, so it was traded for a saturated gold (#d4af37) that reads clearly yellow even
 * though the white label on it is the softest in the set.
 *
 * Changing a value here means re-checking that ratio - a lighter tint looks softer but washes
 * the white label out.
 */
object AvatarColors {
    /**
     * The current palette. Slot order only decides which name lands on which colour (via
     * `hashCode() % size`), so adding an entry re-sorts the whole list - that is expected each
     * time the user edits the set.
     */
    private val palette = intArrayOf(
        0xfff15c80.toInt(),  // red-pink    3.19  (1.1.07, was #d95957 / 3.81)
        0xffc155d4.toInt(),  // purple      3.81
        0xff4bacc6.toInt(),  // cyan-blue   2.62  (1.1.07, was indigo #6278f5 / 3.80)
        0xffd35494.toInt(),  // rose        3.84
        0xff3b9440.toInt(),  // green       3.82  (the lime slot above it was dropped in 1.1.07)
        0xffa37b41.toInt(),  // orange      3.84
        0xffc16c4d.toInt(),  // brown       3.80
        0xff8085e9.toInt(),  // periwinkle  3.26  (1.1.07, new)
        0xffd4af37.toInt()   // gold        2.10  (1.1.07, new - the asked-for "yellow")
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
