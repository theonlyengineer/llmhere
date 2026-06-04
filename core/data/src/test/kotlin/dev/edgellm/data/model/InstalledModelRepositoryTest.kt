package dev.edgellm.data.model

import dev.edgellm.domain.model.InstalledModel
import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstalledModelRepositoryTest {

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
    fun `initially empty`() = runTest {
        val repo = FakeInstalledModelRepository()
        assertTrue(repo.getInstalledModels().first().isEmpty())
    }

    @Test
    fun `mark installed adds model`() = runTest {
        val repo = FakeInstalledModelRepository()
        val model = repo.markInstalled(descriptor, "/data/models/falcon.gguf")
        assertNotNull(model)
        assertEquals(descriptor, model.descriptor)
        assertEquals("/data/models/falcon.gguf", model.localPath)
        assertTrue(model.verified)
    }

    @Test
    fun `get installed model by id`() = runTest {
        val repo = FakeInstalledModelRepository()
        repo.markInstalled(descriptor, "/data/models/falcon.gguf")
        val found = repo.getInstalledModel("falcon-e-1b-instruct")
        assertNotNull(found)
        assertEquals("falcon-e-1b-instruct", found!!.descriptor.id)
    }

    @Test
    fun `get non-existent model returns null`() = runTest {
        val repo = FakeInstalledModelRepository()
        assertNull(repo.getInstalledModel("nonexistent"))
    }

    @Test
    fun `remove model`() = runTest {
        val repo = FakeInstalledModelRepository()
        repo.markInstalled(descriptor, "/data/models/falcon.gguf")
        assertTrue(repo.isInstalled("falcon-e-1b-instruct"))
        repo.removeModel("falcon-e-1b-instruct")
        assertFalse(repo.isInstalled("falcon-e-1b-instruct"))
    }

    @Test
    fun `isInstalled returns false for unknown model`() = runTest {
        val repo = FakeInstalledModelRepository()
        assertFalse(repo.isInstalled("unknown"))
    }
}
