package dev.edgellm.domain.templates

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role

class FalconEdgeTemplate : ChatTemplate {

    override val stopTokens: List<String> = listOf("<|im_end|>")

    override fun format(messages: List<ChatMessage>, systemPrompt: String?): String =
        buildString {
            if (!systemPrompt.isNullOrEmpty()) {
                append("<|im_start|>system\n")
                append(systemPrompt)
                append("<|im_end|>\n")
            }
            messages.forEach { message ->
                val role = when (message.role) {
                    Role.User -> "user"
                    Role.Assistant -> "assistant"
                    Role.System -> "system"
                }
                append("<|im_start|>").append(role).append('\n')
                append(message.content)
                append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
}
