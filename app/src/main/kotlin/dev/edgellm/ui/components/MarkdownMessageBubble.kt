package dev.edgellm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role
import dev.edgellm.ui.theme.LocalChatColors

/**
 * A chat bubble that renders its content as markdown (tables, code, lists, bold/italic, links).
 * User messages stay plain text (no markdown) since they are raw user input.
 */
@Composable
fun MarkdownMessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.User
    val chatColors = LocalChatColors.current
    val textColor = if (isUser) chatColors.userBubbleText else chatColors.assistantBubbleText

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (isUser) Spacer(modifier = Modifier.width(48.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) chatColors.userBubble else chatColors.assistantBubble,
                    shape = MessageBubbleShape(isUser),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (isUser) {
                // User text is raw input — render plain to avoid mangling special chars.
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Markdown(
                    content = message.content,
                    colors = markdownColor(
                        text = textColor,
                        codeText = textColor,
                        linkText = chatColors.sendButton,
                        codeBackground = Color.Black.copy(alpha = 0.08f),
                        inlineCodeBackground = Color.Black.copy(alpha = 0.08f),
                        dividerColor = textColor.copy(alpha = 0.2f),
                        tableText = textColor,
                        tableBackground = Color.Black.copy(alpha = 0.04f),
                    ),
                    typography = markdownTypography(
                        text = MaterialTheme.typography.bodyMedium,
                        code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    ),
                )
            }
        }

        if (!isUser) Spacer(modifier = Modifier.width(48.dp))
    }
}
