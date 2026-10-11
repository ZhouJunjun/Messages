package org.fossify.messages.helpers

import org.joda.time.DateTime
import org.joda.time.LocalDate

/** Time pattern used for messages received/sent today. */
const val TODAY_TIME_PATTERN = "HH:mm:ss"

/**
 * Date + time pattern used for anything older than today, e.g. "26年1月1日 12:30"
 * (1.0.4 spec; was "yy/MM/dd HH:mm" before).
 */
const val NON_TODAY_DATE_TIME_PATTERN = "yy年M月d日 HH:mm"

/**
 * Formats a message timestamp for the conversation list, the starred list and the thread
 * metadata line: only the time for today, the full `26年1月1日 HH:mm` form otherwise.
 */
fun DateTime.formatMessageDateTime(): String {
    val pattern = if (toLocalDate() == LocalDate.now()) TODAY_TIME_PATTERN else NON_TODAY_DATE_TIME_PATTERN
    return toString(pattern)
}
