package dev.edgellm.domain.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatSessionTest {

    @Test
    fun `stores session data`() {
        val session = ChatSession(
            id = "session-1",
            title = "Hello conversation",
            modelId = "falcon-e-1b-instruct",
            messages = listOf(
                ChatMessage(Role.User, "hi"),
                ChatMessage(Role.Assistant, "hello!"),
            ),
            createdAt = 1000L,
            updatedAt = 2000L,
        )
        assertEquals("session-1", session.id)
        assertEquals("Hello conversation", session.title)
        assertEquals("falcon-e-1b-instruct", session.modelId)
        assertEquals(2, session.messages.size)
        assertEquals(1000L, session.createdAt)
        assertEquals(2000L, session.updatedAt)
    }

    @Test
    fun `empty session`() {
        val session = ChatSession(
            id = "s",
            title = "",
            modelId = "falcon-e-1b-instruct",
            messages = emptyList(),
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertTrue(session.messages.isEmpty())
    }
}
