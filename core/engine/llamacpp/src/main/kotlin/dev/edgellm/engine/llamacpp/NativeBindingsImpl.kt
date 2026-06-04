package dev.edgellm.engine.llamacpp

class NativeBindingsImpl : NativeBindings {

    companion object {
        init {
            System.loadLibrary("edgellm_jni")
        }
    }

    external override fun loadModel(
        path: String,
        contextSize: Int,
        gpuLayers: Int,
        useMmap: Boolean,
        threads: Int,
        threadsBatch: Int,
        flashAttention: Boolean,
        kvCacheType: Int,
    ): Long
    external override fun unloadModel(handle: Long)
    external override fun startGeneration(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    )
    external override fun nextToken(handle: Long): String?
    external override fun cancelGeneration(handle: Long)
    external override fun backendVersion(): String
}
