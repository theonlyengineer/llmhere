package dev.edgellm.data.download

import app.cash.turbine.test
import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadManagerTest {

    private val descriptor = ModelDescriptor(
        id = "test-model",
        family = "falcon-e",
        displayName = "Test",
        url = "https://example.com/model.gguf",
        sha256 = "abc",
        sizeBytes = 100L,
        quantization = "i2_s",
        contextLength = 2048,
        minRamMb = 800,
        recommendedRamMb = 1200,
        chatTemplate = "falcon-e",
        stopTokens = listOf("<|im_end|>"),
        engine = "llamacpp",
    )

    @Test
    fun `enqueue adds model id`() {
        val manager = FakeDownloadManager()
        manager.enqueue(descriptor)
        assertEquals(listOf("test-model"), manager.enqueuedModels)
    }

    @Test
    fun `cancel tracks cancelled model`() {
        val manager = FakeDownloadManager()
        manager.cancel("test-model")
        assertEquals(listOf("test-model"), manager.cancelledModels)
    }

    @Test
    fun `observe progress emits updates`() = runTest {
        val manager = FakeDownloadManager()
        manager.enqueue(descriptor)

        manager.observeProgress("test-model").test {
            assertTrue(awaitItem() is DownloadProgress.Queued)

            manager.emitProgress("test-model",
                DownloadProgress.Downloading(50f, 50L, 100L))
            val downloading = awaitItem()
            assertTrue(downloading is DownloadProgress.Downloading)
            assertEquals(50f, (downloading as DownloadProgress.Downloading).percent)

            manager.emitProgress("test-model",
                DownloadProgress.Complete("/models/test.gguf"))
            assertTrue(awaitItem() is DownloadProgress.Complete)
        }
    }
}
