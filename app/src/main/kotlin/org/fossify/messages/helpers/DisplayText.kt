package org.fossify.messages.helpers

/**
 * Characters that a TextView cannot render and that show up as "乱码" boxes, exactly the
 * artifact reported for the conversation list (1.0.3, item 12):
 *
 *  - C0 / C1 control codes, except tab, newline and carriage return;
 *  - zero-width and bidirectional marks;
 *  - line/paragraph separators, the BOM and the Unicode replacement character.
 *
 * Provider snippets occasionally carry such bytes (malformed MMS parts, imported data), and
 * they leak straight into the list because the snippet comes from the telephony provider or
 * from the local cache without any validation.
 */
private val UNRENDERABLE_CHARS = Regex(
    "[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F" +
        "\\u200B-\\u200F\\u2028\\u2029\\u202A-\\u202E\\u2060\\uFEFF\\uFFFD]"
)

/**
 * Removes the unrenderable characters above and trims the result, so the lists never show
 * garbled boxes. Normal messages pass through unchanged.
 */
fun sanitizeForDisplay(text: String): String {
    if (text.isEmpty()) {
        return text
    }

    return UNRENDERABLE_CHARS.replace(text, "").trim()
}
