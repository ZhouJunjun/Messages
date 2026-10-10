package org.fossify.messages.helpers

/**
 * A "copyable string" is a run of 4+ characters from [A-Za-z0-9_-] that is not directly
 * preceded or followed by another such character. This intentionally also matches phone
 * numbers, order numbers, amounts and URLs (per the task definition, kept broad on purpose).
 */
private val COPYABLE_STRING_REGEX = Regex("(?<![\\w-])[\\w-]{4,}(?![\\w-])")

/**
 * Extracts every copyable string from [text].
 * Leading/trailing hyphens are trimmed so that e.g. `编号-1234` yields `1234`.
 * Results keep their order of appearance and duplicates are preserved.
 */
fun extractCopyableStrings(text: String): List<String> {
    if (text.isEmpty()) {
        return emptyList()
    }

    return COPYABLE_STRING_REGEX.findAll(text)
        .map { it.value.trim('-') }
        .filter { it.length >= 4 }
        .toList()
}
