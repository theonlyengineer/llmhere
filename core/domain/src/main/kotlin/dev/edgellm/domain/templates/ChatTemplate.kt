package dev.edgellm.domain.templates

import dev.edgellm.domain.chat.ChatMessage

interface ChatTemplate {
    fun format(messages: List<ChatMessage>, systemPrompt: String?): String
    val stopTokens: List<String>
}
