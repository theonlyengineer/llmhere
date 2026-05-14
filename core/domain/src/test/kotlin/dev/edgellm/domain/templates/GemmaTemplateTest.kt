package dev.edgellm.domain.templates

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GemmaTemplateTest {

    private val template = GemmaTemplate()

    @Test
    fun `formats single user turn with system prompt`() {
        val result = template.format(
            messages = listOf(ChatMessage(Role.User, "hi")),
            systemPrompt = "be brief",
        )
        assertEquals(
            "<start_of_turn>user\nbe brief\nhi<end_of_turn>\n" +
                "<start_of_turn>model\n",
            result,
        )
    }
}
