package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationWithSnippetOverride

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    /**
     * The list snippet comes from the newest local message, but only when that message actually
     * has displayable text. A picture-only MMS is mirrored into the local `messages` table with
     * an empty body, and that empty body used to override the stored snippet and blank the row -
     * the stored snippet is the rebuilt one (e.g. "[图片]", 1.0.4 spec).
     *
     * `last_inbox_snippet` is the newest *received* body of the same thread, taken straight from
     * the local table (1.1.08 fix). The row title of a service sender is read out of the body of
     * its newest received message, and this is the only copy of that body the list can trust:
     * `Conversation.serviceSnippet` is filled in by the provider scan, but the scan only looks a
     * body up when the *provider's* thread snippet has no signature - and on some ROMs that
     * snippet already holds the sender's `【签名】` while the newest local message is our own
     * reply, so the scan stored nothing and `new_snippet` above then replaced the stored body
     * with that reply ("R"). Resolving it here keeps the title independent of the scan.
     */
    @Query(
        "SELECT (SELECT body FROM messages WHERE messages.thread_id = conversations.thread_id" +
            " AND body != '' ORDER BY messages.date DESC LIMIT 1) as new_snippet, " +
            "(SELECT body FROM messages WHERE messages.thread_id = conversations.thread_id" +
            " AND body != '' AND messages.type = 1 AND messages.is_scheduled = 0" +
            " ORDER BY messages.date DESC LIMIT 1) as last_inbox_snippet, * FROM conversations"
    )
    fun getAllWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAll(): List<Conversation> {
        return getAllWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}
