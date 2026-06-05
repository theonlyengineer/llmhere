package dev.edgellm.engine.llamacpp

class FakeNativeBindings(
    private var tokens: List<String> = emptyList(),
    private var shouldFailLoad: Boolean = false,
) : NativeBindings {

    private var handleCounter = 0L
    private var activeHandle: Long = 0L
    private var tokenIndex = 0
    @Volatile
    private var cancelled = false

    override fun loadModel(
        path: String,
        contextSize: Int,
        gpuLayers: Int,
        useMmap: Boolean,
        threads: Int,
        threadsBatch: Int,
        flashAttention: Boolean,
        kvCacheType: Int,
    ): Long {
        if (shouldFailLoad) return 0L
        handleCounter++
        activeHandle = handleCounter
        return activeHandle
    }

    override fun unloadModel(handle: Long) {
        if (handle == activeHandle) {
            activeHandle = 0L
            tokenIndex = 0
            cancelled = false
        }
    }

    override fun startGeneration(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    ) {
        tokenIndex = 0
        cancelled = false
    }

    override fun nextToken(handle: Long): String? {
        if (handle != activeHandle || cancelled || tokenIndex >= tokens.size) return null
        return tokens[tokenIndex++]
    }

    override fun cancelGeneration(handle: Long) {
        cancelled = true
    }

    override fun backendVersion(): String = "fake-1.0"
}
