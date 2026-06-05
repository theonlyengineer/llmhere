package dev.edgellm.data.model

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelImporterTest {

    @TempDir
    lateinit var tempDir: File

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
    fun `imports valid gguf file`() = runTest {
        val repo = FakeInstalledModelRepository()
        val modelsDir = File(tempDir, "models").absolutePath
        val importer = ModelImporter(repo, modelsDir)

        val sourceFile = File(tempDir, "source.gguf")
        // Write GGUF magic bytes + some data
        sourceFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01, 0x02))

        val result = importer.import(sourceFile, descriptor)
        assertTrue(result is ModelImporter.ImportResult.Success)

        val success = result as ModelImporter.ImportResult.Success
        assertEquals("test-model", success.model.descriptor.id)
        assertTrue(File(success.model.localPath).exists())
        assertTrue(repo.isInstalled("test-model"))
    }

    @Test
    fun `rejects non-gguf file`() = runTest {
        val repo = FakeInstalledModelRepository()
        val modelsDir = File(tempDir, "models").absolutePath
        val importer = ModelImporter(repo, modelsDir)

        val sourceFile = File(tempDir, "bad.bin")
        sourceFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val result = importer.import(sourceFile, descriptor)
        assertTrue(result is ModelImporter.ImportResult.Error)
        val error = result as ModelImporter.ImportResult.Error
        assertTrue(error.message.contains("GGUF"))
    }

    @Test
    fun `rejects non-existent file`() = runTest {
        val repo = FakeInstalledModelRepository()
        val modelsDir = File(tempDir, "models").absolutePath
        val importer = ModelImporter(repo, modelsDir)

        val result = importer.import(File("/nonexistent.gguf"), descriptor)
        assertTrue(result is ModelImporter.ImportResult.Error)
    }

    @Test
    fun `validates gguf magic bytes`() {
        val valid = File(tempDir, "valid.gguf")
        valid.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x00))
        assertTrue(ModelImporter.isValidGguf(valid))

        val invalid = File(tempDir, "invalid.bin")
        invalid.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertTrue(!ModelImporter.isValidGguf(invalid))
    }
}
