package org.fossify.messages.helpers

/**
 * Shown in the conversation list / thread metadata when the newest message is a picture-only
 * MMS: the telephony provider's own snippet for such a message is not displayable text, which
 * is what used to show up as garbage in the list (1.0.4 spec).
 */
const val IMAGE_MESSAGE_SNIPPET = "[图片]"

/**
 * Service signature embedded at the start of a message body, e.g. `【京东】` or `[淘宝]`.
 */
private val SERVICE_NAME_REGEX = Regex("[【\\[]([^】\\]]{1,20})[】\\]]")

/**
 * The text inside [IMAGE_MESSAGE_SNIPPET]. It looks like a `[...]` signature, so it must never
 * be mistaken for a service name.
 */
private const val IMAGE_PLACEHOLDER_TEXT = "图片"

/**
 * Decides whether a conversation should be treated as a "service conversation".
 *
 * Classification is purely in-memory (no DB column), based on:
 *  - a service signature such as 【京东】 found in the latest message body, or
 *  - a service-like phone number (short codes, 106 bulk-sender numbers).
 */
fun isServiceConversation(phoneNumber: String, snippet: String): Boolean {
    if (extractServiceName(snippet) != null) {
        return true
    }
    return isServiceNumber(phoneNumber)
}

/**
 * Returns the service name extracted from [snippet], or null when no signature is present.
 * Only the latest message body is inspected (per the agreed design).
 */
fun extractServiceName(snippet: String): String? {
    return SERVICE_NAME_REGEX.findAll(snippet)
        .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
        .firstOrNull { it.isNotEmpty() && !it.equals(IMAGE_PLACEHOLDER_TEXT, ignoreCase = true) }
}

/**
 * Heuristic for "service-like" numbers:
 *  - short codes such as 10086 / 95533 / 12306 (5..8 digits)
 *  - bulk SMS senders starting with 106 (usually 12+ digits)
 */
fun isServiceNumber(phoneNumber: String): Boolean {
    val digits = phoneNumber.filter { it.isDigit() }
    if (digits.isEmpty()) {
        return false
    }

    if (digits.startsWith("106")) {
        return true
    }
    return digits.length in 5..8
}
