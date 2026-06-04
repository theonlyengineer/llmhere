package dev.edgellm.data.chat

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeChatRepository : ChatRepository {

    private val sessions = MutableStateFlow<Map<String, ChatSession>>(emptyMap())

    override suspend fun createSession(modelId: String): ChatSession {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = id,
            title = "",
            modelId = modelId,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        sessions.value = sessions.value + (id to session)
        return session
    }

    override fun getSessions(): Flow<List<ChatSession>> =
        sessions.map { it.values.toList() }

    override suspend fun getSession(id: String): ChatSession? =
        sessions.value[id]

    override suspend fun addMessage(sessionId: String, role: Role, content: String) {
        val session = sessions.value[sessionId] ?: return
        val message = ChatMessage(role, content)
        val updated = session.copy(
            messages = session.messages + message,
            updatedAt = System.currentTimeMillis(),
        )
        sessions.value = sessions.value + (sessionId to updated)
    }

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value - id
    }

    override suspend fun updateSessionTitle(id: String, title: String) {
        val session = sessions.value[id] ?: return
        sessions.value = sessions.value + (id to session.copy(title = title))
    }
}
