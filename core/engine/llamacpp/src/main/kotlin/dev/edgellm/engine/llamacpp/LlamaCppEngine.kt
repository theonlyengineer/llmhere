package dev.edgellm.engine.llamacpp

import dev.edgellm.engine.EngineCapabilities
import dev.edgellm.engine.EngineState
import dev.edgellm.engine.GenerationConfig
import dev.edgellm.engine.InferenceEngine
import dev.edgellm.engine.LoadConfig
import dev.edgellm.engine.ModelHandle
import dev.edgellm.engine.Token
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LlamaCppEngine(
    private val bindings: NativeBindings,
    private val inferenceDispatcher: CoroutineDispatcher,
) : InferenceEngine {

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsGpu = true,
        supportedQuantizations = setOf("i2_s", "Q4_0", "Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0"),
        supportedFamilies = setOf("falcon-e", "gemma", "llama", "qwen", "phi", "mistral", "smollm"),
    )

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state

    private var modelHandle: Long = 0L

    override suspend fun load(model: ModelHandle, config: LoadConfig): Result<Unit> {
        _state.value = EngineState.Loading
        return runCatching {
            val resolvedThreads = if (config.threads > 0) {
                config.threads
            } else {
                Runtime.getRuntime().availableProcessors()
            }
            val resolvedThreadsBatch = if (config.threadsBatch > 0) {
                config.threadsBatch
            } else {
                resolvedThreads
            }
            val kvCacheTypeInt = when (config.kvCacheType) {
                "f32" -> 0
                "f16" -> 1
                "q4_0" -> 2
                "q8_0" -> 8
                else -> 1 // default to f16
            }
            val handle = bindings.loadModel(
                path = model.file.absolutePath,
                contextSize = config.contextSize,
                gpuLayers = config.gpuLayers,
                useMmap = config.useMmap,
                threads = resolvedThreads,
                threadsBatch = resolvedThreadsBatch,
                flashAttention = config.flashAttention,
                kvCacheType = kvCacheTypeInt,
            )
            if (handle == 0L) {
                throw RuntimeException("Failed to load model: ${model.file.name}")
            }
            modelHandle = handle
        }.onSuccess {
            _state.value = EngineState.Ready
        }.onFailure { cause ->
            _state.value = EngineState.Error(cause)
        }
    }

    override suspend fun unload() {
        if (modelHandle != 0L) {
            bindings.unloadModel(modelHandle)
            modelHandle = 0L
        }
        _state.value = EngineState.Idle
    }

    override fun generate(prompt: String, config: GenerationConfig): Flow<Token> = flow {
        _state.value = EngineState.Generating

        bindings.startGeneration(
            handle = modelHandle,
            prompt = prompt,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
            topK = config.topK,
            repeatPenalty = config.repeatPenalty,
        )

        // "One-behind" pattern: hold the current token, peek the next to determine isFinal
        var current = bindings.nextToken(modelHandle)
        while (current != null) {
            val next = bindings.nextToken(modelHandle)
            val isFinal = next == null
            emit(Token(text = current, isFinal = isFinal))
            current = next
        }

        _state.value = EngineState.Ready
    }.flowOn(inferenceDispatcher)

    override fun cancel() {
        if (modelHandle != 0L) {
            bindings.cancelGeneration(modelHandle)
        }
    }
}
