package org.fossify.messages.helpers

import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan

/**
 * A "copyable string" is a run of 4+ characters from [A-Za-z0-9_-] that is not directly
 * preceded or followed by another such character.
 *
 * NOTE: the character class is spelled out in ASCII on purpose. Android's regex engine treats
 * `\w` as Unicode-aware, so `(?<!\w)\w{4,}(?!\w)` also matched whole Chinese sentences. Only
 * ASCII runs (digits/letters/underscore/hyphen) are considered copyable now.
 */
private val COPYABLE_STRING_REGEX = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{4,}(?![A-Za-z0-9_-])")

/** Anything with an explicit scheme, e.g. `http://10086.com`, `smsto:10086`. */
private val SCHEME_URL_REGEX = Regex("(?i)[a-z][a-z0-9+.\\-]*://\\S+")

/** Bare domains, e.g. `10086.com`, `m.10010.cn/qAZRw?u=1ce...`. */
private val BARE_DOMAIN_REGEX = Regex("(?i)(?:[a-z0-9_-]+\\.)+[a-z]{2,6}(?:/\\S*)?")

/**
 * Trailing non-URL run a greedy `\S+` may have swallowed: CJK punctuation, ideographs, kana and
 * full-width forms. Chinese messages routinely glue the next sentence straight onto the URL
 * ("点击https://a.cn/xyz了解详情"), and that tail must not become part of the link.
 */
private val TRAILING_NON_URL_REGEX = Regex("[\\u2E80-\\u9FFF\\uFF00-\\uFFEF\\uD800-\\uDFFF]+$")

/**
 * Extracts every copyable string from [text].
 *
 *  - strings that live *inside a link* are never extracted (so `访问：http://10086.com` yields
 *    nothing, and `m.10010.cn/qAZRw?u=1ce...` contributes no fragments);
 *  - leading/trailing hyphens are trimmed so that e.g. `编号-1234` yields `1234`;
 *  - duplicates are collapsed, so `哈哈哈12345好东东活动12345` yields a single `12345`.
 *
 * Results keep their order of appearance.
 */
fun extractCopyableStrings(text: String): List<String> {
    if (text.isEmpty()) {
        return emptyList()
    }

    // blank out links (same length, so nothing else shifts) before scanning for copyable strings
    var masked = SCHEME_URL_REGEX.replace(text) { " ".repeat(it.value.length) }
    masked = BARE_DOMAIN_REGEX.replace(masked) { " ".repeat(it.value.length) }

    return COPYABLE_STRING_REGEX.findAll(masked)
        .map { it.value.trim('-') }
        .filter { it.length >= 4 }
        .distinct()
        .toList()
}

/**
 * Turns every URL in [text] into a [URLSpan], so the TextView paints it with its `linkTextColor`.
 *
 * The message body used to rely on `android:autoLink="email|web"`, whose framework pattern does
 * not accept a hyphen inside a domain label: `https://1-t.cn/6zS6zla4f4` was only linked from
 * `t.cn` on, leaving `https://1-` in the normal text colour (1.0.8 spec). The URL shapes used here
 * are the same ones [extractCopyableStrings] masks out, so a URL never contributes copyable
 * strings and is always coloured as one link.
 */
fun linkifyUrls(text: CharSequence): CharSequence {
    if (text.isEmpty()) {
        return text
    }

    val spannable = SpannableString(text)
    val taken = ArrayList<IntRange>()

    fun addMatches(regex: Regex) {
        regex.findAll(text).forEach { match ->
            // trim the CJK tail a greedy match may have grabbed, then skip if nothing is left
            val tail = TRAILING_NON_URL_REGEX.find(match.value)?.value?.length ?: 0
            val start = match.range.first
            val end = match.range.last - tail
            if (end < start) {
                return@forEach
            }
            if (taken.any { it.first <= end && start <= it.last }) {
                return@forEach
            }

            taken.add(start..end)
            spannable.setSpan(
                URLSpan(text.subSequence(start, end + 1).toString()),
                start,
                end + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    // schemes first, so a bare-domain match inside the same URL is recognised as an overlap
    addMatches(SCHEME_URL_REGEX)
    addMatches(BARE_DOMAIN_REGEX)
    return spannable
}
