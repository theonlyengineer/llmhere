package dev.edgellm.engine

data class GenerationConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.3f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.3f,
    val stopTokens: List<String> = emptyList(),
)
