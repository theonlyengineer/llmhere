package dev.edgellm.data.chat

import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.chat.Role
import dev.edgellm.engine.GenerationConfig
import dev.edgellm.engine.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

private const val DEFAULT_HISTORY_TURNS = 3

class ChatSessionManager(
    private val chatRepository: ChatRepository,
    private val promptBuilder: PromptBuilder,
    private val engine: InferenceEngine,
    private val family: String,
    systemPrompt: String?,
    private val contextLength: Int,
    private val stopTokens: List<String>,
    generationConfig: GenerationConfig = GenerationConfig(),
    historyTurns: Int = DEFAULT_HISTORY_TURNS,
) {
    var systemPrompt: String? = systemPrompt
    var generationConfig: GenerationConfig = generationConfig

    /** How many prior user/assistant turns to include as context (0 = stateless). */
    var historyTurns: Int = historyTurns

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession

    private val _assistantMessage = MutableStateFlow("")
    val assistantMessage: StateFlow<String> = _assistantMessage

    suspend fun newSession(modelId: String): ChatSession {
        val session = chatRepository.createSession(modelId)
        _currentSession.value = session
        _assistantMessage.value = ""
        return session
    }

    suspend fun switchSession(sessionId: String) {
        val session = chatRepository.getSession(sessionId)
        _currentSession.value = session
        _assistantMessage.value = ""
    }

    fun getSessions(): Flow<List<ChatSession>> =
        chatRepository.getSessions()

    suspend fun sendMessage(content: String) {
        val session = _currentSession.value ?: return

        // Add user message
        chatRepository.addMessage(session.id, Role.User, content)
        val updatedSession = chatRepository.getSession(session.id) ?: return
        _currentSession.value = updatedSession

        // Context-aware history: a continuation ("continue", "go on", "more") keeps the
        // prior turns so the model can carry on; a brand-new question starts fresh so a
        // small model isn't dragged back to the previous topic.
        val turns = if (isContinuation(content)) historyTurns else 0
        val recentMessages = updatedSession.messages.takeLast(turns * 2 + 1)
        val prompt = promptBuilder.build(
            family = family,
            messages = recentMessages,
            systemPrompt = systemPrompt,
            contextLength = contextLength,
        )

        // Generate response
        _assistantMessage.value = ""
        val responseBuilder = StringBuilder()

        val config = generationConfig.copy(stopTokens = stopTokens)
        engine.generate(prompt, config).onEach { token ->
            responseBuilder.append(token.text)
            _assistantMessage.value = responseBuilder.toString()
        }.collect()

        // Persist assistant message
        val finalResponse = responseBuilder.toString()
        chatRepository.addMessage(session.id, Role.Assistant, finalResponse)
        _currentSession.value = chatRepository.getSession(session.id)
    }

    /**
     * True when [message] is a short follow-up that should continue the previous
     * exchange (e.g. "continue", "go on", "tell me more") rather than start a new topic.
     */
    private fun isContinuation(message: String): Boolean {
        val normalized = message.trim().lowercase().trimEnd('.', '!', '?', ',', ' ')
        if (normalized.isEmpty()) return false
        if (normalized.split(WHITESPACE).size > MAX_CONTINUATION_WORDS) return false
        return CONTINUATION_PHRASES.any { normalized == it || normalized.startsWith("$it ") }
    }

    fun cancelGeneration() {
        engine.cancel()
    }

    suspend fun deleteSession(sessionId: String) {
        chatRepository.deleteSession(sessionId)
        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = null
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_CONTINUATION_WORDS = 4
        val CONTINUATION_PHRASES = listOf(
            "continue", "go on", "keep going", "carry on", "go ahead", "proceed",
            "more", "tell me more", "say more", "and then", "then", "next",
            "elaborate", "explain more", "what else", "anything else", "and", "go on please",
        )
    }
}
