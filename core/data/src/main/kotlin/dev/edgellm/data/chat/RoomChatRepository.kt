package dev.edgellm.data.chat

import dev.edgellm.data.chat.db.ChatDao
import dev.edgellm.data.chat.db.ChatMessageEntity
import dev.edgellm.data.chat.db.ChatSessionEntity
import dev.edgellm.data.chat.db.toDomain
import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Room-backed [ChatRepository]. Sessions and messages survive process death. */
class RoomChatRepository(private val dao: ChatDao) : ChatRepository {

    override suspend fun createSession(modelId: String): ChatSession {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsertSession(
            ChatSessionEntity(
                id = id,
                modelId = modelId,
                title = "",
                createdAt = now,
                updatedAt = now,
            ),
        )
        return ChatSession(
            id = id,
            title = "",
            modelId = modelId,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }

    override fun getSessions(): Flow<List<ChatSession>> =
        dao.observeSessionsWithMessages().map { list -> list.map { it.toDomain() } }

    override suspend fun getSession(id: String): ChatSession? =
        dao.getSessionWithMessages(id)?.toDomain()

    override suspend fun addMessage(sessionId: String, role: Role, content: String) {
        val position = dao.messageCount(sessionId)
        dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role.name,
                content = content,
                position = position,
            ),
        )
        dao.touchSession(sessionId, System.currentTimeMillis())
    }

    override suspend fun deleteSession(id: String) {
        dao.deleteSession(id)
    }

    override suspend fun updateSessionTitle(id: String, title: String) {
        dao.updateTitle(id, title)
    }
}
