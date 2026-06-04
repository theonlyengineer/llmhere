package dev.edgellm.engine.llamacpp

interface NativeBindings {
    fun loadModel(
        path: String,
        contextSize: Int,
        gpuLayers: Int,
        useMmap: Boolean,
        threads: Int,
        threadsBatch: Int,
        flashAttention: Boolean,
        kvCacheType: Int,
    ): Long
    fun unloadModel(handle: Long)
    fun startGeneration(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    )
    fun nextToken(handle: Long): String?
    fun cancelGeneration(handle: Long)
    fun backendVersion(): String
}
