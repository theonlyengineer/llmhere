package dev.edgellm.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FakeInferenceEngine(
    var emits: List<String> = emptyList(),
    var loadResult: Result<Unit> = Result.success(Unit),
) : InferenceEngine {

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsGpu = false,
        supportedQuantizations = setOf("i2_s", "Q4_K_M"),
        supportedFamilies = setOf("falcon-e", "gemma"),
    )

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state

    @Volatile
    private var cancelled = false

    override suspend fun load(model: ModelHandle, config: LoadConfig): Result<Unit> {
        _state.value = EngineState.Loading
        return loadResult.also { result ->
            _state.value = if (result.isSuccess) EngineState.Ready else
                EngineState.Error(result.exceptionOrNull()!!)
        }
    }

    override suspend fun unload() {
        _state.value = EngineState.Idle
        cancelled = false
    }

    override fun generate(prompt: String, config: GenerationConfig): Flow<Token> = flow {
        _state.value = EngineState.Generating
        cancelled = false
        val tokens = emits.toList()
        for ((index, text) in tokens.withIndex()) {
            if (cancelled) break
            val isFinal = index == tokens.lastIndex
            emit(Token(text = text, isFinal = isFinal))
        }
        _state.value = EngineState.Ready
    }

    override fun cancel() {
        cancelled = true
    }
}
