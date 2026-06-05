package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerationConfigTest {

    @Test
    fun `default values`() {
        val config = GenerationConfig()
        assertEquals(80, config.maxTokens)
        assertEquals(0.3f, config.temperature)
        assertEquals(0.9f, config.topP)
        assertEquals(40, config.topK)
        assertEquals(1.3f, config.repeatPenalty)
        assertTrue(config.stopTokens.isEmpty())
    }

    @Test
    fun `custom values`() {
        val config = GenerationConfig(
            maxTokens = 1024,
            temperature = 0.0f,
            topP = 1.0f,
            topK = 50,
            repeatPenalty = 1.2f,
            stopTokens = listOf("<|im_end|>"),
        )
        assertEquals(1024, config.maxTokens)
        assertEquals(0.0f, config.temperature)
        assertEquals(listOf("<|im_end|>"), config.stopTokens)
    }
}
