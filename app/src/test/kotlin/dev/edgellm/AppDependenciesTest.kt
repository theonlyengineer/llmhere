package dev.edgellm

import dev.edgellm.data.settings.GenerationSettings
import dev.edgellm.data.settings.GenerationSettingsRepository
import dev.edgellm.engine.FakeInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDependenciesTest {

    private class FakeSettingsRepository : GenerationSettingsRepository {
        override fun getSettings(): Flow<GenerationSettings> = flowOf(GenerationSettings())
        override suspend fun update(settings: GenerationSettings) {}
    }

    private fun createDeps() = AppDependencies(
        engine = FakeInferenceEngine(),
        modelsDir = "/tmp/models",
        settingsRepository = FakeSettingsRepository(),
    )

    @Test
    fun `all components are wired`() {
        val deps = createDeps()
        assertNotNull(deps.chatTemplateRegistry)
        assertNotNull(deps.promptBuilder)
        assertNotNull(deps.chatRepository)
        assertNotNull(deps.installedModelRepository)
        assertNotNull(deps.downloader)
        assertNotNull(deps.modelCatalog)
        assertNotNull(deps.engine)
    }

    @Test
    fun `loadCatalog parses valid json`() {
        val deps = createDeps()
        val json = """
        [
          {
            "id": "falcon-e-1b-instruct",
            "family": "falcon-e",
            "display_name": "Falcon-E 1B Instruct",
            "url": "https://example.com/model.gguf",
            "sha256": "",
            "size_bytes": 665722880,
            "quantization": "i2_s",
            "context_length": 2048,
            "min_ram_mb": 1024,
            "recommended_ram_mb": 2048,
            "chat_template": "falcon-e",
            "stop_tokens": ["<|im_end|>"],
            "engine": "llamacpp"
          }
        ]
        """.trimIndent()
        deps.loadCatalog(json)
        assertEquals(1, deps.catalogModels.size)
        assertEquals("falcon-e-1b-instruct", deps.catalogModels[0].id)
    }

    @Test
    fun `catalogModels is initially empty`() {
        val deps = createDeps()
        assertTrue(deps.catalogModels.isEmpty())
    }
}
