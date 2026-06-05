package dev.edgellm.engine.llamacpp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FakeNativeBindingsTest {

    @Test
    fun `load returns valid handle`() {
        val bindings = FakeNativeBindings()
        val handle = bindings.loadModel("/fake.gguf", 2048, 0, true, 4, 4, true, 1)
        assertNotEquals(0L, handle)
    }

    @Test
    fun `load failure returns zero handle`() {
        val bindings = FakeNativeBindings(shouldFailLoad = true)
        val handle = bindings.loadModel("/fake.gguf", 2048, 0, true, 4, 4, true, 1)
        assertEquals(0L, handle)
    }

    @Test
    fun `generate returns tokens then null`() {
        val bindings = FakeNativeBindings(tokens = listOf("Hello", " world"))
        val handle = bindings.loadModel("/fake.gguf", 2048, 0, true, 4, 4, true, 1)
        bindings.startGeneration(handle, "prompt", 100, 0.7f, 0.9f, 40, 1.1f)
        assertEquals("Hello", bindings.nextToken(handle))
        assertEquals(" world", bindings.nextToken(handle))
        assertNull(bindings.nextToken(handle))
    }

    @Test
    fun `unload resets state`() {
        val bindings = FakeNativeBindings(tokens = listOf("a"))
        val handle = bindings.loadModel("/fake.gguf", 2048, 0, true, 4, 4, true, 1)
        bindings.startGeneration(handle, "prompt", 100, 0.7f, 0.9f, 40, 1.1f)
        assertEquals("a", bindings.nextToken(handle))
        bindings.unloadModel(handle)
        // After unload, nextToken returns null
        assertNull(bindings.nextToken(handle))
    }

    @Test
    fun `cancel stops generation`() {
        val bindings = FakeNativeBindings(tokens = listOf("a", "b", "c"))
        val handle = bindings.loadModel("/fake.gguf", 2048, 0, true, 4, 4, true, 1)
        bindings.startGeneration(handle, "prompt", 100, 0.7f, 0.9f, 40, 1.1f)
        assertEquals("a", bindings.nextToken(handle))
        bindings.cancelGeneration(handle)
        assertNull(bindings.nextToken(handle))
    }

    @Test
    fun `backendVersion returns fake version`() {
        val bindings = FakeNativeBindings()
        assertEquals("fake-1.0", bindings.backendVersion())
    }
}
