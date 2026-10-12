package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * One `conversations` row decorated with the two message bodies the list needs but the table does
 * not hold directly.
 *
 * [snippet] is the newest local message of the thread. [lastInboxSnippet] is its newest *received*
 * message, which is where a service sender's `【签名】` lives - see the query in `ConversationsDao`
 * for why the stored `service_snippet` column cannot be relied on here (1.1.08 fix).
 */
data class ConversationWithSnippetOverride(
    @ColumnInfo(name = "new_snippet") val snippet: String?,
    @ColumnInfo(name = "last_inbox_snippet") val lastInboxSnippet: String?,
    @Embedded val conversation: Conversation
) {
    fun toConversation(): Conversation {
        var result = if (snippet == null) {
            conversation
        } else {
            conversation.copy(snippet = snippet)
        }

        // The persisted column is only right when the provider scan had a reason to look the body
        // up; when it is still empty the newest received body comes from the local table instead.
        // Without this the row falls back to `snippet` - our own reply - and titles itself with the
        // bare number (the service-thread bug reported on 1.1.06, 1.1.07 and 1.1.08).
        if (result.serviceSnippet.isEmpty() && !lastInboxSnippet.isNullOrEmpty()) {
            result = result.copy(serviceSnippet = lastInboxSnippet)
        }
        return result
    }
}
