package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ModelHandleTest {

    @Test
    fun `stores model metadata`() {
        val handle = ModelHandle(
            file = File("/data/models/falcon-e-1b.gguf"),
            family = "falcon-e",
            quantization = "i2_s",
            contextLength = 2048,
        )
        assertEquals(File("/data/models/falcon-e-1b.gguf"), handle.file)
        assertEquals("falcon-e", handle.family)
        assertEquals("i2_s", handle.quantization)
        assertEquals(2048, handle.contextLength)
    }

    @Test
    fun `data class equality`() {
        val a = ModelHandle(File("/a.gguf"), "falcon-e", "i2_s", 2048)
        val b = ModelHandle(File("/a.gguf"), "falcon-e", "i2_s", 2048)
        assertEquals(a, b)
    }
}
