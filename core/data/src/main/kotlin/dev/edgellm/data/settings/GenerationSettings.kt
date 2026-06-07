package dev.edgellm.data.settings

data class GenerationSettings(
    val temperature: Float = 0.3f,
    val repeatPenalty: Float = 1.3f,
    val maxTokens: Int = 512,
    val systemPrompt: String = "You are a helpful assistant. Answer questions clearly and concisely.",
    val thinkingEnabled: Boolean = false,
    /** Prior user/assistant turns kept as context (0 = stateless). */
    val historyTurns: Int = 3,
)
