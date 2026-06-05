package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EngineCapabilitiesTest {

    @Test
    fun `stores capabilities`() {
        val caps = EngineCapabilities(
            supportsGpu = false,
            supportedQuantizations = setOf("Q4_K_M", "i2_s"),
            supportedFamilies = setOf("falcon-e"),
        )
        assertFalse(caps.supportsGpu)
        assertEquals(setOf("Q4_K_M", "i2_s"), caps.supportedQuantizations)
        assertEquals(setOf("falcon-e"), caps.supportedFamilies)
    }

    @Test
    fun `data class equality`() {
        val a = EngineCapabilities(true, setOf("Q4_K_M"), setOf("gemma"))
        val b = EngineCapabilities(true, setOf("Q4_K_M"), setOf("gemma"))
        assertEquals(a, b)
    }
}
