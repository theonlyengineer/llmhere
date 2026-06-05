package dev.edgellm.domain.chat

data class ChatSession(
    val id: String,
    val title: String,
    val modelId: String,
    val messages: List<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long,
)
