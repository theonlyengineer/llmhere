package dev.edgellm.domain.templates

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FalconEdgeTemplateTest {

    private val template = FalconEdgeTemplate()

    @Test
    fun `formats single user turn with system prompt`() {
        val result = template.format(
            messages = listOf(ChatMessage(Role.User, "hello")),
            systemPrompt = "You are helpful.",
        )
        assertEquals(
            "<|im_start|>system\nYou are helpful.<|im_end|>\n" +
                "<|im_start|>user\nhello<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `formats single user turn without system prompt`() {
        val result = template.format(
            messages = listOf(ChatMessage(Role.User, "hello")),
            systemPrompt = null,
        )
        assertEquals(
            "<|im_start|>user\nhello<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `formats multi-turn conversation`() {
        val result = template.format(
            messages = listOf(
                ChatMessage(Role.User, "hi"),
                ChatMessage(Role.Assistant, "hello!"),
                ChatMessage(Role.User, "how are you?"),
            ),
            systemPrompt = "be brief",
        )
        assertEquals(
            "<|im_start|>system\nbe brief<|im_end|>\n" +
                "<|im_start|>user\nhi<|im_end|>\n" +
                "<|im_start|>assistant\nhello!<|im_end|>\n" +
                "<|im_start|>user\nhow are you?<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `trailing assistant generation prompt always appended`() {
        val result = template.format(
            messages = listOf(
                ChatMessage(Role.User, "test"),
                ChatMessage(Role.Assistant, "response"),
            ),
            systemPrompt = null,
        )
        assert(result.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `handles empty content`() {
        val result = template.format(
            messages = listOf(ChatMessage(Role.User, "")),
            systemPrompt = null,
        )
        assertEquals(
            "<|im_start|>user\n<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `handles content containing template-like tokens`() {
        val result = template.format(
            messages = listOf(ChatMessage(Role.User, "what does <|im_start|> mean?")),
            systemPrompt = null,
        )
        assertEquals(
            "<|im_start|>user\nwhat does <|im_start|> mean?<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `stopTokens contains im_end`() {
        assertEquals(listOf("<|im_end|>"), template.stopTokens)
    }
}
