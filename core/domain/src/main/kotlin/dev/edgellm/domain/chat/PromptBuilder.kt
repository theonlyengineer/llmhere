package dev.edgellm.domain.chat

import dev.edgellm.domain.templates.ChatTemplateRegistry

class PromptBuilder(private val registry: ChatTemplateRegistry) {

    fun build(
        family: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        contextLength: Int,
    ): String {
        val template = registry.templateFor(family) ?: return ""

        // Estimate max chars from context length (rough: 4 chars per token)
        val maxChars = contextLength * 4

        // Trim older messages to fit within context, always keeping the last message
        val trimmed = trimToFit(messages, systemPrompt, maxChars)

        return template.format(trimmed, systemPrompt)
    }

    private fun trimToFit(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        maxChars: Int,
    ): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        val systemOverhead = systemPrompt?.length ?: 0
        val templateOverhead = 100 // rough overhead for template markers
        val available = maxChars - systemOverhead - templateOverhead

        // Build from the end, keeping as many recent messages as fit
        val result = mutableListOf<ChatMessage>()
        var totalChars = 0

        for (message in messages.reversed()) {
            val msgChars = message.content.length + 30 // overhead for role markers
            if (totalChars + msgChars > available && result.isNotEmpty()) {
                break
            }
            result.add(0, message)
            totalChars += msgChars
        }

        return result
    }
}
