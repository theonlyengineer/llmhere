package dev.edgellm.data.device

import dev.edgellm.domain.model.ModelDescriptor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryCheckerTest {

    private val descriptor = ModelDescriptor(
        id = "falcon-e-1b-instruct",
        family = "falcon-e",
        displayName = "Falcon-E 1B",
        url = "https://example.com/model.gguf",
        sha256 = "abc",
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
    fun `ok when available ram exceeds recommended`() {
        val checker = FakeMemoryChecker(availableRamMb = 2000)
        val result = checker.canLoadModel(descriptor)
        assertTrue(result is MemoryCheckResult.Ok)
    }

    @Test
    fun `low ram when between min and recommended`() {
        val checker = FakeMemoryChecker(availableRamMb = 1000)
        val result = checker.canLoadModel(descriptor)
        assertTrue(result is MemoryCheckResult.LowRam)
        val lowRam = result as MemoryCheckResult.LowRam
        assertTrue(lowRam.available == 1000)
        assertTrue(lowRam.recommended == 1200)
    }

    @Test
    fun `insufficient when below min`() {
        val checker = FakeMemoryChecker(availableRamMb = 500)
        val result = checker.canLoadModel(descriptor)
        assertTrue(result is MemoryCheckResult.InsufficientRam)
        val insufficient = result as MemoryCheckResult.InsufficientRam
        assertTrue(insufficient.available == 500)
        assertTrue(insufficient.minimum == 800)
    }

    @Test
    fun `reports available ram`() {
        val checker = FakeMemoryChecker(availableRamMb = 4096)
        assertTrue(checker.availableRamMb() == 4096)
    }
}
