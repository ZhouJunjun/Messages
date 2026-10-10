package org.fossify.messages.helpers

/**
 * Service signature embedded at the start of a message body, e.g. `【京东】` or `[淘宝]`.
 */
private val SERVICE_NAME_REGEX = Regex("[【\\[]([^】\\]]{1,20})[】\\]]")

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
    return SERVICE_NAME_REGEX.find(snippet)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
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

/**
 * The display name of a service conversation: the extracted signature, falling back to
 * the phone number when no signature can be found.
 */
fun serviceDisplayName(phoneNumber: String, snippet: String): String {
    return extractServiceName(snippet) ?: phoneNumber
}
