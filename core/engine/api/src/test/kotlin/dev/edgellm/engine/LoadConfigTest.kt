package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadConfigTest {

    @Test
    fun `default values`() {
        val config = LoadConfig()
        assertEquals(2048, config.contextSize)
        assertEquals(0, config.gpuLayers)
        assertTrue(config.useMmap)
        assertEquals("f16", config.kvCacheType)
    }

    @Test
    fun `custom values`() {
        val config = LoadConfig(
            contextSize = 4096,
            gpuLayers = 32,
            useMmap = false,
            kvCacheType = "q8_0",
        )
        assertEquals(4096, config.contextSize)
        assertEquals(32, config.gpuLayers)
        assertEquals(false, config.useMmap)
        assertEquals("q8_0", config.kvCacheType)
    }
}
