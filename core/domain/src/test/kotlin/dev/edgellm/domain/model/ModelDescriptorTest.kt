package dev.edgellm.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ModelDescriptorTest {

    private val descriptor = ModelDescriptor(
        id = "falcon-e-1b-instruct",
        family = "falcon-e",
        displayName = "Falcon-E 1B Instruct",
        url = "https://huggingface.co/tiiuae/Falcon-E-1B-Instruct-GGUF/resolve/main/ggml-model-i2_s.gguf",
        sha256 = "abc123",
        sizeBytes = 665_000_000L,
        quantization = "i2_s",
        contextLength = 2048,
        minRamMb = 800,
        recommendedRamMb = 1200,
        chatTemplate = "falcon-e",
        stopTokens = listOf("<|im_end|>"),
        engine = "llamacpp",
    )

    @Test
    fun `stores all fields`() {
        assertEquals("falcon-e-1b-instruct", descriptor.id)
        assertEquals("falcon-e", descriptor.family)
        assertEquals("Falcon-E 1B Instruct", descriptor.displayName)
        assertEquals("i2_s", descriptor.quantization)
        assertEquals(2048, descriptor.contextLength)
        assertEquals(800, descriptor.minRamMb)
        assertEquals(listOf("<|im_end|>"), descriptor.stopTokens)
        assertEquals("llamacpp", descriptor.engine)
    }

    @Test
    fun `data class equality`() {
        val copy = descriptor.copy()
        assertEquals(descriptor, copy)
    }

    @Test
    fun `different id means not equal`() {
        val other = descriptor.copy(id = "falcon-e-3b-instruct")
        assertNotEquals(descriptor, other)
    }
}
