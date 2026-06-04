package dev.edgellm.engine

data class LoadConfig(
    val contextSize: Int = 2048,
    val gpuLayers: Int = 0,
    val useMmap: Boolean = true,
    val kvCacheType: String = "f16",
    val threads: Int = 0,
    val threadsBatch: Int = 0,
    val flashAttention: Boolean = true,
)
