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
 * 🔴 The palette is the former Material 400 set with grey mixed in (1.1.03 spec: "在原来的8个上
 * 加灰调"). Each entry keeps its predecessor's *hue* and only trades saturation and lightness
 * away, so a row still reads as "red", "purple", "indigo"... while a screenful of avatars stops
 * behaving like a rainbow. Slots 4 and 5 are the one exception: 4/5/6 used to sit on adjacent
 * hues (blue 199 / teal 174 / green 123) and read as three near-identical blue-greens, so 6
 * (green) is kept and the other two moved to the widest gaps left on the wheel (rose 330, lime 75).
 *
 * Every entry is picked as a *background*: the label on top of it is always pure white
 * (`Color.WHITE`, set by both adapters), so each colour must stay dark enough for the white
 * glyphs to sit on it. The grey factor is the transform's only free parameter - 60% saturation
 * (the "light grey" tier the user picked), lightness solved until the ratio clears 3.8:1 against
 * #FFFFFF (WCAG relative-luminance formula, computed rather than eyeballed):
 *
 *   #EF5350 red    -> #d95957 3.81    #AB47BC purple -> #c155d4 3.81
 *   #5C6BC0 indigo -> #6278f5 3.80    #29B6F6 blue   -> #d35494 3.84   (slot 4, hue 199 -> 330)
 *   #26A69A teal   -> #768b38 3.80    #66BB6A green  -> #3b9440 3.82   (slot 5, hue 174 -> 75)
 *   #FFA726 orange -> #a37b41 3.84    #8D6E63 brown  -> #c16c4d 3.80
 *
 * Changing a value here means re-checking that ratio - a lighter tint looks softer but washes
 * the white label out.
 */
object AvatarColors {
    /**
     * Greyed-down keepsakes of the original Material 400 palette, ordered as before so a given
     * name keeps the colour family it has always had (slots 4/5 moved to rose/lime, see above).
     */
    private val palette = intArrayOf(
        0xffd95957.toInt(),  // red
        0xffc155d4.toInt(),  // purple
        0xff6278f5.toInt(),  // indigo
        0xffd35494.toInt(),  // rose  (was blue, hue 199 -> 330)
        0xff768b38.toInt(),  // lime  (was teal, hue 174 -> 75)
        0xff3b9440.toInt(),  // green
        0xffa37b41.toInt(),  // orange
        0xffc16c4d.toInt()   // brown
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
