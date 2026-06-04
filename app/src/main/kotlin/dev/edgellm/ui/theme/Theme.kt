package dev.edgellm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ChatColors(
    val userBubble: Color,
    val userBubbleText: Color,
    val assistantBubble: Color,
    val assistantBubbleText: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val sendButton: Color,
    val sendButtonIcon: Color,
)

private val LightChatColors = ChatColors(
    userBubble = Color(0xFF32628D),
    userBubbleText = Color.White,
    assistantBubble = Color(0xFFE9EEF6),
    assistantBubbleText = Color(0xFF1B1C1D),
    inputBackground = Color.White,
    inputBorder = Color(0xFFD0D5DD),
    sendButton = Color(0xFF32628D),
    sendButtonIcon = Color.White,
)

private val DarkChatColors = ChatColors(
    userBubble = Color(0xFF1F3760),
    userBubbleText = Color(0xFFE0E0E0),
    assistantBubble = Color(0xFF2A2B2D),
    assistantBubbleText = Color(0xFFE0E0E0),
    inputBackground = Color(0xFF1B1C1D),
    inputBorder = Color(0xFF3A3B3D),
    sendButton = Color(0xFF4A7DB5),
    sendButtonIcon = Color.White,
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }

@Composable
fun EdgeLlmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val chatColors = if (darkTheme) DarkChatColors else LightChatColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalChatColors provides chatColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
