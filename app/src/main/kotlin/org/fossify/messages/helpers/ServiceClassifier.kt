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
 * Classification is purely in-memory (no DB column) and, since the 1.1.04 fix, based on the
 * *sender number alone*:
 *  - a service-like number (short codes, hotlines, 106 bulk senders, 0-prefixed landlines) is
 *    a service conversation, unless the sender is a real contact from the address book;
 *  - everything else (personal mobile numbers) is a regular conversation.
 *
 * A `【签名】` in the body is NO LONGER enough on its own: a private message reading
 * "【急】晚上聚餐" used to flip its thread into the service tab (and its title into "急") purely
 * because of the brackets. It also made the classification follow the newest message, so a
 * thread kept hopping between tabs. [extractServiceName] is still used for *display* - an
 * unknown service number without a contact still shows its signature as the row title.
 *
 * @param hasContactName true when the sender resolves to an actual contact name; a contact is
 *   always classified as a regular conversation, even when its number looks service-like
 *   (1.1.04 fix).
 */
fun isServiceConversation(
    phoneNumber: String,
    @Suppress("UNUSED_PARAMETER") snippet: String,
    hasContactName: Boolean = false,
): Boolean {
    if (hasContactName) {
        return false
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
 * Heuristic for "service-like" numbers (1.1.05 rework):
 *  - bulk SMS senders starting with 106 (usually 12+ digits)
 *  - fixed-line numbers starting with 0, area code included: personal texts never come from a
 *    landline, so 菜鸟驿站 (0452-…), 税务局 (020-…) & co all land in the service tab
 *  - operator hotlines starting with 10: 10000 / 10010 / 10086 / 1001295 …
 *  - 400 / 800 enterprise hotlines
 *  - remaining short codes up to 8 digits: 95533, 12306, 95xxx …
 *
 * A personal mobile is exactly 11 digits starting 1[3-9] - everything else that reaches 3
 * digits counts as service. Numbers stored in international form (86 + 11 digits) are
 * normalised first, so a contact-less "+8613…" thread stays a regular conversation.
 *
 * 🔴 History: 3..6 digits only (landline owners leaked into the service tab), then 1.1.04
 * dropped 7..8-digit numbers for the same reason; 1.1.05 flips landline handling - with
 * contacts already guarded by [isServiceConversation], an unlisted landline or hotline sender
 * is service in practice, which is what the user actually sees in the wild.
 */
fun isServiceNumber(phoneNumber: String): Boolean {
    var digits = phoneNumber.filter { it.isDigit() }

    // international form of a Chinese mobile: 86 + 11 digits -> drop the 86 prefix
    if (digits.length == 13 && digits.startsWith("86")) {
        digits = digits.substring(2)
    }

    if (digits.length < 3) {
        return false
    }

    if (digits.startsWith("106")) {
        return true
    }
    if (digits.startsWith("0")) {
        return true
    }
    // a personal mobile: 11 digits starting 1[3-9]
    if (digits.length == 11 && digits[0] == '1' && digits[1] in '3'..'9') {
        return false
    }
    if (digits.startsWith("10")) {
        return true
    }
    if (digits.startsWith("400") || digits.startsWith("800")) {
        return true
    }
    return digits.length <= 8
}
