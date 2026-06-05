package dev.edgellm.engine

import app.cash.turbine.test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class FakeInferenceEngineTest {

    private val dummyModel = ModelHandle(
        file = File("/tmp/fake.gguf"),
        family = "falcon-e",
        quantization = "i2_s",
        contextLength = 2048,
    )

    @Test
    fun `initial state is idle`() {
        val engine = FakeInferenceEngine()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `load transitions to ready`() = runTest {
        val engine = FakeInferenceEngine()
        engine.state.test {
            assertEquals(EngineState.Idle, awaitItem())
            engine.load(dummyModel)
            assertEquals(EngineState.Loading, awaitItem())
            assertEquals(EngineState.Ready, awaitItem())
        }
    }

    @Test
    fun `load failure transitions to error`() = runTest {
        val cause = RuntimeException("oom")
        val engine = FakeInferenceEngine(loadResult = Result.failure(cause))
        val result = engine.load(dummyModel)
        assertTrue(result.isFailure)
        assertTrue(engine.state.value is EngineState.Error)
    }

    @Test
    fun `generate emits configured tokens`() = runTest {
        val engine = FakeInferenceEngine(emits = listOf("Hello", " world", "!"))
        engine.load(dummyModel)
        val tokens = engine.generate("prompt").toList()
        assertEquals(3, tokens.size)
        assertEquals("Hello", tokens[0].text)
        assertEquals(" world", tokens[1].text)
        assertEquals("!", tokens[2].text)
        assertTrue(tokens[2].isFinal)
        assertEquals(false, tokens[0].isFinal)
    }

    @Test
    fun `unload transitions to idle`() = runTest {
        val engine = FakeInferenceEngine()
        engine.load(dummyModel)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }
}
