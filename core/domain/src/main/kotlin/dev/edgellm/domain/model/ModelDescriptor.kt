package dev.edgellm.domain.model

data class ModelDescriptor(
    val id: String,
    val family: String,
    val displayName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val quantization: String,
    val contextLength: Int,
    val minRamMb: Int,
    val recommendedRamMb: Int,
    val chatTemplate: String,
    val stopTokens: List<String>,
    val engine: String,
)
