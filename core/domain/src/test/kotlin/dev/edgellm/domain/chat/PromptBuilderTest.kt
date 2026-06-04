package dev.edgellm.domain.chat

import dev.edgellm.domain.templates.ChatTemplateRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val registry = ChatTemplateRegistry()
    private val builder = PromptBuilder(registry)

    @Test
    fun `builds prompt from messages using correct template`() {
        val messages = listOf(
            ChatMessage(Role.User, "hello"),
        )
        val result = builder.build(
            family = "falcon-e",
            messages = messages,
            systemPrompt = "You are helpful.",
            contextLength = 4096,
        )
        assertEquals(
            "<|im_start|>system\nYou are helpful.<|im_end|>\n" +
                "<|im_start|>user\nhello<|im_end|>\n" +
                "<|im_start|>assistant\n",
            result,
        )
    }

    @Test
    fun `builds prompt with gemma template`() {
        val messages = listOf(
            ChatMessage(Role.User, "hi"),
        )
        val result = builder.build(
            family = "gemma",
            messages = messages,
            systemPrompt = "be brief",
            contextLength = 4096,
        )
        assertTrue(result.contains("<start_of_turn>user"))
        assertTrue(result.contains("be brief"))
    }

    @Test
    fun `truncates old messages when exceeding context`() {
        val longContent = "x".repeat(1000)
        val messages = listOf(
            ChatMessage(Role.User, longContent),
            ChatMessage(Role.Assistant, longContent),
            ChatMessage(Role.User, longContent),
            ChatMessage(Role.Assistant, longContent),
            ChatMessage(Role.User, "latest question"),
        )
        // contextLength=80 → maxChars=320. Only the last short message fits.
        val result = builder.build(
            family = "falcon-e",
            messages = messages,
            systemPrompt = "sys",
            contextLength = 80,
        )
        // The latest message should always be included
        assertTrue(result.contains("latest question"))
        // Old long messages should be truncated
        assertFalse(result.contains(longContent))
    }

    @Test
    fun `keeps system prompt always`() {
        val longContent = "x".repeat(500)
        val messages = listOf(
            ChatMessage(Role.User, longContent),
            ChatMessage(Role.User, "final"),
        )
        val result = builder.build(
            family = "falcon-e",
            messages = messages,
            systemPrompt = "important system prompt",
            contextLength = 200,
        )
        assertTrue(result.contains("important system prompt"))
        assertTrue(result.contains("final"))
    }

    @Test
    fun `handles empty conversation`() {
        val result = builder.build(
            family = "falcon-e",
            messages = emptyList(),
            systemPrompt = "sys",
            contextLength = 4096,
        )
        assertTrue(result.contains("sys"))
        assertTrue(result.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `returns empty string for unknown family`() {
        val result = builder.build(
            family = "unknown",
            messages = listOf(ChatMessage(Role.User, "hi")),
            systemPrompt = null,
            contextLength = 4096,
        )
        assertEquals("", result)
    }
}
