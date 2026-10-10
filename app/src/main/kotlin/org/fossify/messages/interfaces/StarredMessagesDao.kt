package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.StarredMessage

@Dao
interface StarredMessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(starredMessage: StarredMessage)

    @Query("DELETE FROM starred_messages WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM starred_messages")
    fun deleteAll()

    @Query("SELECT * FROM starred_messages")
    fun getAll(): List<StarredMessage>

    @Query("SELECT id FROM starred_messages")
    fun getAllIds(): List<Long>

    @Query("SELECT COUNT(*) FROM starred_messages")
    fun getCount(): Int
}
