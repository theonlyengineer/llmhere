package dev.edgellm.data.chat.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory hand-written stand-in for [ChatDao] used in JVM unit tests.
 * Mirrors the SQL semantics: cascade delete, position ordering, updatedAt DESC.
 */
class FakeChatDao : ChatDao {

    private val sessions = MutableStateFlow<Map<String, ChatSessionEntity>>(emptyMap())
    private val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var autoId = 1L

    override suspend fun upsertSession(session: ChatSessionEntity) {
        sessions.value = sessions.value + (session.id to session)
    }

    override fun observeSessionsWithMessages(): Flow<List<SessionWithMessages>> =
        sessions.map { map ->
            map.values
                .sortedByDescending { it.updatedAt }
                .map { s ->
                    SessionWithMessages(
                        session = s,
                        messages = messages.value.filter { it.sessionId == s.id },
                    )
                }
        }

    override suspend fun getSessionWithMessages(id: String): SessionWithMessages? {
        val s = sessions.value[id] ?: return null
        return SessionWithMessages(
            session = s,
            messages = messages.value.filter { it.sessionId == id },
        )
    }

    override suspend fun insertMessage(message: ChatMessageEntity) {
        val withId = if (message.id == 0L) message.copy(id = autoId++) else message
        messages.value = messages.value + withId
    }

    override suspend fun messageCount(sessionId: String): Int =
        messages.value.count { it.sessionId == sessionId }

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value - id
        messages.value = messages.value.filterNot { it.sessionId == id } // cascade
    }

    override suspend fun updateTitle(id: String, title: String) {
        sessions.value[id]?.let { sessions.value = sessions.value + (id to it.copy(title = title)) }
    }

    override suspend fun touchSession(id: String, updatedAt: Long) {
        sessions.value[id]?.let { sessions.value = sessions.value + (id to it.copy(updatedAt = updatedAt)) }
    }
}
