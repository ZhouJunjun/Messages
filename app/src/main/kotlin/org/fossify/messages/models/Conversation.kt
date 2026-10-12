package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations", indices = [(Index(value = ["thread_id"], unique = true))])
data class Conversation(
    @PrimaryKey @ColumnInfo(name = "thread_id") var threadId: Long,
    @ColumnInfo(name = "snippet") var snippet: String,
    @ColumnInfo(name = "date") var date: Int,
    @ColumnInfo(name = "read") var read: Boolean,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "photo_uri") var photoUri: String,
    @ColumnInfo(name = "is_group_conversation") var isGroupConversation: Boolean,
    @ColumnInfo(name = "phone_number") var phoneNumber: String,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false,
    @ColumnInfo(name = "uses_custom_title") var usesCustomTitle: Boolean = false,
    @ColumnInfo(name = "unread_count") var unreadCount: Int = 0,
) {

    /**
     * Body of the newest *received* message of this thread, filled in while the provider is scanned.
     *
     * Not persisted (`@Ignore`), deliberately. [snippet] is the newest message of *either* direction,
     * so as soon as we reply inside a service thread the snippet becomes our own text: the sender's
     * `【签名】` is gone and the row fell back to showing the bare number (1.1.06 fix). The display
     * layer reads the `【签名】` from here instead and falls back to [snippet] when this is empty
     * (threads we never received anything in, or rows rebuilt from the database).
     *
     * Declared in the class body rather than as a constructor parameter so Room never has to reason
     * about a non-column constructor argument; the trade-off is that `copy()` does not carry it -
     * harmless, since every provider scan fills it in again.
     */
    @Ignore
    var serviceSnippet: String = ""

    companion object {
        fun areItemsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.threadId == new.threadId
        }

        fun areContentsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.snippet == new.snippet &&
                old.date == new.date &&
                old.read == new.read &&
                old.title == new.title &&
                old.photoUri == new.photoUri &&
                old.isGroupConversation == new.isGroupConversation &&
                old.phoneNumber == new.phoneNumber &&
                old.isScheduled == new.isScheduled &&
                old.unreadCount == new.unreadCount &&
                old.serviceSnippet == new.serviceSnippet
        }
    }
}
