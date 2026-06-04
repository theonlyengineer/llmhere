package dev.edgellm.data.chat

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.Role
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun createSession(modelId: String): ChatSession
    fun getSessions(): Flow<List<ChatSession>>
    suspend fun getSession(id: String): ChatSession?
    suspend fun addMessage(sessionId: String, role: Role, content: String)
    suspend fun deleteSession(id: String)
    suspend fun updateSessionTitle(id: String, title: String)
}
