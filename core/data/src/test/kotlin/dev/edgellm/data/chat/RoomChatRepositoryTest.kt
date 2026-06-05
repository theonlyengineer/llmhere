package dev.edgellm.data.chat

import dev.edgellm.data.chat.db.FakeChatDao
import dev.edgellm.domain.chat.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomChatRepositoryTest {

    private fun repo() = RoomChatRepository(FakeChatDao())

    @Test
    fun `create session persists and is retrievable`() = runTest {
        val r = repo()
        val session = r.createSession("falcon-e-1b-instruct")
        assertNotNull(session.id)
        assertEquals("falcon-e-1b-instruct", session.modelId)
        assertTrue(session.messages.isEmpty())

        val found = r.getSession(session.id)
        assertNotNull(found)
        assertEquals(session.id, found!!.id)
    }

    @Test
    fun `add messages preserves order`() = runTest {
        val r = repo()
        val s = r.createSession("m")
        r.addMessage(s.id, Role.User, "hello")
        r.addMessage(s.id, Role.Assistant, "hi there")
        r.addMessage(s.id, Role.User, "bye")

        val loaded = r.getSession(s.id)!!
        assertEquals(3, loaded.messages.size)
        assertEquals(Role.User, loaded.messages[0].role)
        assertEquals("hello", loaded.messages[0].content)
        assertEquals(Role.Assistant, loaded.messages[1].role)
        assertEquals("bye", loaded.messages[2].content)
    }

    @Test
    fun `get sessions ordered by most recently updated`() = runTest {
        val r = repo()
        val a = r.createSession("m1")
        val b = r.createSession("m2")
        // Touch a after b by adding a message to it
        r.addMessage(a.id, Role.User, "ping")

        val sessions = r.getSessions().first()
        assertEquals(2, sessions.size)
        assertEquals(a.id, sessions[0].id) // a was updated most recently
    }

    @Test
    fun `delete session removes it and its messages`() = runTest {
        val r = repo()
        val s = r.createSession("m")
        r.addMessage(s.id, Role.User, "hi")
        r.deleteSession(s.id)

        assertNull(r.getSession(s.id))
        assertTrue(r.getSessions().first().isEmpty())
    }

    @Test
    fun `update title changes session title`() = runTest {
        val r = repo()
        val s = r.createSession("m")
        r.updateSessionTitle(s.id, "My chat")
        assertEquals("My chat", r.getSession(s.id)!!.title)
    }
}
