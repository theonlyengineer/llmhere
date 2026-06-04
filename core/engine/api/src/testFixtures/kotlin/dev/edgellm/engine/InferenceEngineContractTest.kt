package dev.edgellm.engine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

abstract class InferenceEngineContractTest {

    abstract fun createEngine(scope: TestScope): InferenceEngine

    private val dummyModel = ModelHandle(
        file = File("/tmp/fake-model.gguf"),
        family = "falcon-e",
        quantization = "i2_s",
        contextLength = 2048,
    )

    @Test
    fun `initial state is idle`() = runTest {
        val engine = createEngine(this)
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `load transitions to ready on success`() = runTest {
        val engine = createEngine(this)
        val result = engine.load(dummyModel)
        assertTrue(result.isSuccess)
        assertEquals(EngineState.Ready, engine.state.value)
    }

    @Test
    fun `unload transitions to idle`() = runTest {
        val engine = createEngine(this)
        engine.load(dummyModel)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `unload is idempotent`() = runTest {
        val engine = createEngine(this)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `generate emits tokens`() = runTest {
        val engine = createEngine(this)
        engine.load(dummyModel)
        val tokens = engine.generate("hello", GenerationConfig()).toList()
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.last().isFinal)
    }

    @Test
    fun `cancel stops generation`() = runTest {
        val engine = createEngine(this)
        engine.load(dummyModel)
        engine.cancel()
    }
}
