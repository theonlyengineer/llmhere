package dev.edgellm.data.chat

import app.cash.turbine.test
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.chat.Role
import dev.edgellm.domain.templates.ChatTemplateRegistry
import dev.edgellm.engine.FakeInferenceEngine
import dev.edgellm.engine.LoadConfig
import dev.edgellm.engine.ModelHandle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ChatSessionManagerTest {

    private val registry = ChatTemplateRegistry()
    private val promptBuilder = PromptBuilder(registry)

    private fun createManager(
        emits: List<String> = listOf("Hello", " world", "!"),
    ): Pair<ChatSessionManager, FakeInferenceEngine> {
        val engine = FakeInferenceEngine(emits = emits)
        val manager = ChatSessionManager(
            chatRepository = FakeChatRepository(),
            promptBuilder = promptBuilder,
            engine = engine,
            family = "falcon-e",
            systemPrompt = "You are helpful.",
            contextLength = 2048,
            stopTokens = listOf("<|im_end|>"),
        )
        return manager to engine
    }

    @Test
    fun `new session creates and sets current`() = runTest {
        val (manager, engine) = createManager()
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        val session = manager.newSession("falcon-e-1b-instruct")
        assertNotNull(session)
        assertEquals("falcon-e-1b-instruct", session.modelId)
        assertEquals(session, manager.currentSession.value)
    }

    @Test
    fun `send message adds user and assistant messages`() = runTest {
        val (manager, engine) = createManager(emits = listOf("Hi", " there"))
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        manager.newSession("falcon-e-1b-instruct")

        manager.sendMessage("hello")

        val session = manager.currentSession.value!!
        assertEquals(2, session.messages.size)
        assertEquals(Role.User, session.messages[0].role)
        assertEquals("hello", session.messages[0].content)
        assertEquals(Role.Assistant, session.messages[1].role)
        assertEquals("Hi there", session.messages[1].content)
    }

    @Test
    fun `assistant message updates in real time`() = runTest {
        val (manager, engine) = createManager(emits = listOf("A", "B", "C"))
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        manager.newSession("falcon-e-1b-instruct")

        manager.assistantMessage.test {
            assertEquals("", awaitItem()) // initial

            manager.sendMessage("hi")

            // Collect intermediate states — at minimum we should see the final value
            val values = mutableListOf<String>()
            while (true) {
                val item = awaitItem()
                values.add(item)
                if (item == "ABC") break
            }
            assertTrue(values.contains("ABC"))
        }
    }

    @Test
    fun `switch session changes current`() = runTest {
        val (manager, engine) = createManager()
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        val session1 = manager.newSession("model-1")
        val session2 = manager.newSession("model-2")

        manager.switchSession(session1.id)
        assertEquals(session1.id, manager.currentSession.value!!.id)
    }

    @Test
    fun `delete session clears current if active`() = runTest {
        val (manager, engine) = createManager()
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        val session = manager.newSession("falcon-e-1b-instruct")

        manager.deleteSession(session.id)
        assertNull(manager.currentSession.value)
    }

    @Test
    fun `cancel generation delegates to engine`() = runTest {
        val (manager, engine) = createManager()
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        // Should not throw
        manager.cancelGeneration()
    }

    @Test
    fun `continuation message keeps prior context`() = runTest {
        val (manager, engine) = createManager(emits = listOf("ok"))
        engine.load(ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048), LoadConfig())
        manager.newSession("model")
        manager.historyTurns = 3

        engine.emits = listOf("India is a country in South Asia.")
        manager.sendMessage("tell me about India")

        engine.emits = listOf("ok")
        manager.sendMessage("continue")

        val prompt = engine.lastPrompt
        assert(prompt.contains("India")) {
            "A continuation must keep prior context, but prompt was:\n$prompt"
        }
        assert(prompt.contains("continue")) { "Current message must be in prompt" }
    }

    @Test
    fun `new topic question ignores prior context`() = runTest {
        val (manager, engine) = createManager(emits = listOf("ok"))
        engine.load(ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048), LoadConfig())
        manager.newSession("model")
        manager.historyTurns = 3

        engine.emits = listOf("India is a country in South Asia.")
        manager.sendMessage("tell me about India")

        // A brand-new question should NOT drag in the India context.
        engine.emits = listOf("ok")
        manager.sendMessage("how to make a tea?")

        val prompt = engine.lastPrompt
        assert(!prompt.contains("India")) {
            "A new question must start fresh, but prompt still had India:\n$prompt"
        }
        assert(prompt.contains("tea")) { "Current question must be in prompt" }
    }

    @Test
    fun `continuation history is trimmed to the configured number of turns`() = runTest {
        val (manager, engine) = createManager(emits = listOf("ok"))
        engine.load(ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048), LoadConfig())
        manager.newSession("model")
        manager.historyTurns = 2

        repeat(4) { i ->
            engine.emits = listOf("answer$i")
            manager.sendMessage("question$i")
        }
        // A continuation pulls in history, trimmed to the last 2 turns.
        engine.emits = listOf("final")
        manager.sendMessage("continue")

        val prompt = engine.lastPrompt
        assert(!prompt.contains("question0")) { "Old history should be trimmed" }
        assert(!prompt.contains("question1")) { "Old history should be trimmed" }
        assert(prompt.contains("question3")) { "Recent history should be kept" }
    }

    @Test
    fun `get sessions returns flow`() = runTest {
        val (manager, engine) = createManager()
        engine.load(
            ModelHandle(File("/fake.gguf"), "falcon-e", "i2_s", 2048),
            LoadConfig(),
        )
        manager.newSession("model-1")
        manager.newSession("model-2")

        manager.getSessions().test {
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
        }
    }
}
