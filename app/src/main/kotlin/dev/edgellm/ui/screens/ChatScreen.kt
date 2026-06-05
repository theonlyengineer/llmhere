package dev.edgellm.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.Role
import dev.edgellm.domain.model.ModelDescriptor
import dev.edgellm.ui.components.MarkdownMessageBubble
import dev.edgellm.ui.components.MessageBubbleShape
import dev.edgellm.ui.components.ModelSelectorBottomSheet
import dev.edgellm.ui.theme.LocalChatColors
import dev.edgellm.viewmodel.ChatUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    assistantMessage: String,
    currentModel: ModelDescriptor?,
    catalogModels: List<ModelDescriptor>,
    downloadedModelIds: Set<String>,
    onDownloadClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onRetry: () -> Unit,
    onSelectModel: (ModelDescriptor) -> Unit,
    onRestartModel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showModelSelector by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ModelChip(
                        modelName = currentModel?.displayName ?: "EdgeLLM",
                        onClick = { showModelSelector = true },
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                is ChatUiState.NoModel -> NoModelContent(onDownloadClick)
                is ChatUiState.Downloading -> DownloadingContent(uiState)
                is ChatUiState.LoadingModel -> LoadingModelContent()
                is ChatUiState.Ready -> ReadyContent(
                    uiState = uiState,
                    assistantMessage = assistantMessage,
                    onSendMessage = onSendMessage,
                    onCancelGeneration = onCancelGeneration,
                    onImageClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Image support coming soon")
                        }
                    },
                )
                is ChatUiState.Error -> ErrorContent(uiState, onRetry)
            }
        }
    }

    if (showModelSelector) {
        ModelSelectorBottomSheet(
            currentModelId = currentModel?.id,
            catalogModels = catalogModels,
            downloadedModelIds = downloadedModelIds,
            onSelectModel = onSelectModel,
            onRestartModel = onRestartModel,
            onDismiss = { showModelSelector = false },
        )
    }
}

@Composable
private fun ModelChip(modelName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = modelName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Change model",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NoModelContent(onDownloadClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("No model installed", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Download a model to start chatting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDownloadClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalChatColors.current.sendButton,
            ),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download & Try")
        }
    }
}

@Composable
private fun DownloadingContent(state: ChatUiState.Downloading) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Downloading model...", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            strokeCap = StrokeCap.Round,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${state.percent.toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${state.bytesDownloaded / 1_000_000}MB / ${state.totalBytes / 1_000_000}MB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingModelContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Loading model...", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a moment",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyContent(
    uiState: ChatUiState.Ready,
    assistantMessage: String,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onImageClick: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val displayMessages = buildList {
        addAll(uiState.messages)
        if (uiState.isGenerating && assistantMessage.isNotEmpty()) {
            add(ChatMessage(Role.Assistant, assistantMessage))
        }
    }

    LaunchedEffect(displayMessages.size, assistantMessage) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (displayMessages.isEmpty() && !uiState.isGenerating) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Start a conversation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type a message below to begin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(displayMessages) { message ->
                    MarkdownMessageBubble(message)
                }
                if (uiState.isGenerating && assistantMessage.isEmpty()) {
                    item { TypingIndicator() }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }

        ChatInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isGenerating = uiState.isGenerating,
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText.trim())
                    inputText = ""
                }
            },
            onCancel = onCancelGeneration,
            onImageClick = onImageClick,
        )
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onImageClick: () -> Unit,
) {
    val chatColors = LocalChatColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Image attach placeholder (OCR phase)
        IconButton(
            onClick = onImageClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Attach image",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Type a message...", style = MaterialTheme.typography.bodyMedium)
            },
            enabled = !isGenerating,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = chatColors.sendButton,
                unfocusedBorderColor = chatColors.inputBorder,
            ),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (isGenerating) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop generating", modifier = Modifier.size(24.dp))
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                enabled = inputText.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = chatColors.sendButton,
                    contentColor = chatColors.sendButtonIcon,
                    disabledContainerColor = chatColors.sendButton.copy(alpha = 0.4f),
                    disabledContentColor = chatColors.sendButtonIcon.copy(alpha = 0.4f),
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val chatColors = LocalChatColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-alpha",
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .background(color = chatColors.assistantBubble, shape = MessageBubbleShape(isUser = false))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    val delay = index * 200
                    val alpha = if ((dotAlpha * 1000).toInt() % 600 > delay) 1f else 0.3f
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(chatColors.assistantBubbleText.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(state: ChatUiState.Error, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(24.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}
