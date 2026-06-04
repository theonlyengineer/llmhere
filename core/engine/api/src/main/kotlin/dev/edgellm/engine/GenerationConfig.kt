package dev.edgellm.engine

data class GenerationConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopTokens: List<String> = emptyList(),
)
