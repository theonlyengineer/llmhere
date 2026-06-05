package dev.edgellm.data.chat.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Transaction
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessionsWithMessages(): Flow<List<SessionWithMessages>>

    @Transaction
    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionWithMessages(id: String): SessionWithMessages?

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun messageCount(sessionId: String): Int

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: String, updatedAt: Long)
}
