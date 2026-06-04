package dev.edgellm.data.chat

import dev.edgellm.domain.chat.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryChatRepositoryTest {

    @Test
    fun `create session`() = runTest {
        val repo = InMemoryChatRepository()
        val session = repo.createSession("falcon-e-1b-instruct")
        assertNotNull(session.id)
        assertEquals("falcon-e-1b-instruct", session.modelId)
        assertTrue(session.messages.isEmpty())
    }

    @Test
    fun `get sessions returns all`() = runTest {
        val repo = InMemoryChatRepository()
        repo.createSession("model-1")
        repo.createSession("model-2")
        val sessions = repo.getSessions().first()
        assertEquals(2, sessions.size)
    }

    @Test
    fun `get session by id`() = runTest {
        val repo = InMemoryChatRepository()
        val created = repo.createSession("falcon-e-1b-instruct")
        val found = repo.getSession(created.id)
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `get non-existent session returns null`() = runTest {
        val repo = InMemoryChatRepository()
        assertNull(repo.getSession("nonexistent"))
    }

    @Test
    fun `add message to session`() = runTest {
        val repo = InMemoryChatRepository()
        val session = repo.createSession("falcon-e-1b-instruct")
        repo.addMessage(session.id, Role.User, "hello")
        repo.addMessage(session.id, Role.Assistant, "hi!")

        val updated = repo.getSession(session.id)!!
        assertEquals(2, updated.messages.size)
        assertEquals(Role.User, updated.messages[0].role)
        assertEquals("hello", updated.messages[0].content)
        assertEquals(Role.Assistant, updated.messages[1].role)
    }

    @Test
    fun `delete session`() = runTest {
        val repo = InMemoryChatRepository()
        val session = repo.createSession("falcon-e-1b-instruct")
        repo.deleteSession(session.id)
        assertNull(repo.getSession(session.id))
    }

    @Test
    fun `update session title`() = runTest {
        val repo = InMemoryChatRepository()
        val session = repo.createSession("falcon-e-1b-instruct")
        repo.updateSessionTitle(session.id, "My Chat")
        val updated = repo.getSession(session.id)!!
        assertEquals("My Chat", updated.title)
    }
}
