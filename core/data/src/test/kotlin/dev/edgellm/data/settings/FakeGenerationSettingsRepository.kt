package dev.edgellm.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGenerationSettingsRepository : GenerationSettingsRepository {
    private val _settings = MutableStateFlow(GenerationSettings())

    override fun getSettings(): Flow<GenerationSettings> = _settings

    override suspend fun update(settings: GenerationSettings) {
        _settings.value = settings
    }
}
