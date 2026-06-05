package dev.edgellm.data.settings

import kotlinx.coroutines.flow.Flow

interface GenerationSettingsRepository {
    fun getSettings(): Flow<GenerationSettings>
    suspend fun update(settings: GenerationSettings)
}
