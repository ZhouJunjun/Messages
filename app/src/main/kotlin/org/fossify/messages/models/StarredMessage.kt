package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Marks a message as starred (favorite) by the user.
 *
 * The row only holds the id of the message and the time it was starred, exactly like
 * the removed recycle bin table did — [Message.id] borrows the telephony provider's
 * `sms._id`, so the `messages` table can be wiped and rebuilt from the provider at any
 * time without losing this user data.
 */
@Entity(tableName = "starred_messages", indices = [(Index(value = ["id"], unique = true))])
data class StarredMessage(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "starred_ts") val starredTs: Long,
)
