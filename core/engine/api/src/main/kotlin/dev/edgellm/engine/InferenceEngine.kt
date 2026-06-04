package dev.edgellm.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InferenceEngine {
    val capabilities: EngineCapabilities
    val state: StateFlow<EngineState>

    suspend fun load(model: ModelHandle, config: LoadConfig = LoadConfig()): Result<Unit>
    suspend fun unload()

    fun generate(prompt: String, config: GenerationConfig = GenerationConfig()): Flow<Token>
    fun cancel()
}
