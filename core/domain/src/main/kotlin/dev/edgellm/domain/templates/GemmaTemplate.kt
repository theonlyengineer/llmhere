package dev.edgellm.domain.templates

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role

class GemmaTemplate : ChatTemplate {

    override val stopTokens: List<String> = listOf("<end_of_turn>")

    override fun format(messages: List<ChatMessage>, systemPrompt: String?): String =
        buildString {
            messages.forEach { message ->
                val role = if (message.role == Role.Assistant) "model" else "user"
                append("<start_of_turn>").append(role).append('\n')
                if (!systemPrompt.isNullOrEmpty()) append(systemPrompt).append('\n')
                append(message.content).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
}
