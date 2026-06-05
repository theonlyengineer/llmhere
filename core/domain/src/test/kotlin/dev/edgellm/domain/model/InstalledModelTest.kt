package dev.edgellm.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstalledModelTest {

    private val descriptor = ModelDescriptor(
        id = "falcon-e-1b-instruct",
        family = "falcon-e",
        displayName = "Falcon-E 1B Instruct",
        url = "https://example.com/model.gguf",
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
    fun `stores installed model info`() {
        val installed = InstalledModel(
            descriptor = descriptor,
            localPath = "/data/models/falcon-e-1b.gguf",
            downloadedAt = 1000L,
            lastUsedAt = 2000L,
            verified = true,
        )
        assertEquals(descriptor, installed.descriptor)
        assertEquals("/data/models/falcon-e-1b.gguf", installed.localPath)
        assertEquals(1000L, installed.downloadedAt)
        assertEquals(2000L, installed.lastUsedAt)
        assertTrue(installed.verified)
    }

    @Test
    fun `unverified model`() {
        val installed = InstalledModel(
            descriptor = descriptor,
            localPath = "/data/models/falcon-e-1b.gguf",
            downloadedAt = 1000L,
            lastUsedAt = 1000L,
            verified = false,
        )
        assertFalse(installed.verified)
    }
}
