package org.fossify.messages.helpers

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
