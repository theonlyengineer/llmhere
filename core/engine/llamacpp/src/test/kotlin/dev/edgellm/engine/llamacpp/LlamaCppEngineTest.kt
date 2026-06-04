package dev.edgellm.engine.llamacpp

import app.cash.turbine.test
import dev.edgellm.engine.EngineState
import dev.edgellm.engine.GenerationConfig
import dev.edgellm.engine.ModelHandle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LlamaCppEngineTest {

    private val dummyModel = ModelHandle(
        file = File("/tmp/fake.gguf"),
        family = "falcon-e",
        quantization = "i2_s",
        contextLength = 2048,
    )

    private fun createEngine(
        tokens: List<String> = emptyList(),
        shouldFailLoad: Boolean = false,
    ): LlamaCppEngine {
        val bindings = FakeNativeBindings(tokens = tokens, shouldFailLoad = shouldFailLoad)
        return LlamaCppEngine(bindings, UnconfinedTestDispatcher())
    }

    @Test
    fun `initial state is idle`() {
        val engine = createEngine()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `load transitions through loading to ready`() = runTest {
        val engine = createEngine()
        engine.state.test {
            assertEquals(EngineState.Idle, awaitItem())
            engine.load(dummyModel)
            assertEquals(EngineState.Loading, awaitItem())
            assertEquals(EngineState.Ready, awaitItem())
        }
    }

    @Test
    fun `load failure transitions to error`() = runTest {
        val engine = createEngine(shouldFailLoad = true)
        val result = engine.load(dummyModel)
        assertTrue(result.isFailure)
        assertTrue(engine.state.value is EngineState.Error)
    }

    @Test
    fun `unload transitions to idle`() = runTest {
        val engine = createEngine()
        engine.load(dummyModel)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `unload is idempotent`() = runTest {
        val engine = createEngine()
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `generate emits tokens with isFinal on last`() = runTest {
        val engine = createEngine(tokens = listOf("Hello", " world", "!"))
        engine.load(dummyModel)
        val tokens = engine.generate("test prompt").toList()
        assertEquals(3, tokens.size)
        assertEquals("Hello", tokens[0].text)
        assertFalse(tokens[0].isFinal)
        assertEquals(" world", tokens[1].text)
        assertFalse(tokens[1].isFinal)
        assertEquals("!", tokens[2].text)
        assertTrue(tokens[2].isFinal)
    }

    @Test
    fun `generate returns to ready state after completion`() = runTest {
        val engine = createEngine(tokens = listOf("a"))
        engine.load(dummyModel)
        assertEquals(EngineState.Ready, engine.state.value)
        val tokens = engine.generate("prompt").toList()
        assertEquals(1, tokens.size)
        assertEquals(EngineState.Ready, engine.state.value)
    }

    @Test
    fun `cancel stops generation`() = runTest {
        val engine = createEngine(tokens = listOf("a", "b", "c", "d"))
        engine.load(dummyModel)
        engine.cancel()
        assertEquals(EngineState.Ready, engine.state.value)
    }

    @Test
    fun `capabilities reflect llama cpp support`() {
        val engine = createEngine()
        assertTrue(engine.capabilities.supportedFamilies.isNotEmpty())
        assertTrue(engine.capabilities.supportedQuantizations.isNotEmpty())
    }
}
