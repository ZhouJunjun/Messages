package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `serviceSnippet` is a real column (1.1.08), not an in-memory extra. It holds the body of the
 * newest *received* message of the thread, which is where the sender's `【签名】` lives.
 *
 * It used to be a `@Ignore`'d property filled in during the provider scan - and that silently did
 * nothing: the list is always rebuilt from `conversationsDB.getAll()`, so the scan's value was
 * dropped on the way to the UI and a service thread we had replied to kept showing its bare number
 * as the title. `snippet` cannot stand in for it: that one is direction-agnostic, so as soon as we
 * reply inside a service thread it holds our own text ("R", "退订"…) and the signature is gone
 * (1.1.08 fix - reported twice, 1.1.06 and again on 1.1.07).
 */
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
    /** Body of the newest received message; see the class KDoc. */
    @ColumnInfo(name = "service_snippet") var serviceSnippet: String = "",
) {

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
