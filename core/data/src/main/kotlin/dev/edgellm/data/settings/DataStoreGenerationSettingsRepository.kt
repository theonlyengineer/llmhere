package dev.edgellm.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("generation_settings")

class DataStoreGenerationSettingsRepository(context: Context) : GenerationSettingsRepository {

    private val store = context.dataStore

    private object Keys {
        val temperature    = floatPreferencesKey("temperature")
        val repeatPenalty  = floatPreferencesKey("repeat_penalty")
        val maxTokens      = intPreferencesKey("max_tokens")
        val systemPrompt   = stringPreferencesKey("system_prompt")
        val thinkingEnabled = booleanPreferencesKey("thinking_enabled")
        val historyTurns   = intPreferencesKey("history_turns")
    }

    private val defaults = GenerationSettings()

    override fun getSettings(): Flow<GenerationSettings> = store.data.map { prefs ->
        GenerationSettings(
            temperature     = prefs[Keys.temperature]     ?: defaults.temperature,
            repeatPenalty   = prefs[Keys.repeatPenalty]   ?: defaults.repeatPenalty,
            maxTokens       = prefs[Keys.maxTokens]       ?: defaults.maxTokens,
            systemPrompt    = prefs[Keys.systemPrompt]    ?: defaults.systemPrompt,
            thinkingEnabled = prefs[Keys.thinkingEnabled] ?: defaults.thinkingEnabled,
            historyTurns    = prefs[Keys.historyTurns]    ?: defaults.historyTurns,
        )
    }

    override suspend fun update(settings: GenerationSettings) {
        store.edit { prefs ->
            prefs[Keys.temperature]     = settings.temperature
            prefs[Keys.repeatPenalty]   = settings.repeatPenalty
            prefs[Keys.maxTokens]       = settings.maxTokens
            prefs[Keys.systemPrompt]    = settings.systemPrompt
            prefs[Keys.thinkingEnabled] = settings.thinkingEnabled
            prefs[Keys.historyTurns]    = settings.historyTurns
        }
    }
}
