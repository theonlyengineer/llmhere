package dev.edgellm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edgellm.domain.model.ModelDescriptor

/** Per-model state used to choose the card's primary action. */
enum class ModelCardState { NotDownloaded, Downloaded, Active, Downloading, Loading }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    models: List<ModelDescriptor>,
    downloadedModelIds: Set<String>,
    activeModelId: String?,
    downloadingModelId: String?,
    downloadPercent: Float,
    loadingModelId: String?,
    onDownload: (ModelDescriptor) -> Unit,
    onRun: (ModelDescriptor) -> Unit,
    onChat: (ModelDescriptor) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("EdgeLLM", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(models, key = { it.id }) { model ->
                val state = when {
                    model.id == downloadingModelId -> ModelCardState.Downloading
                    model.id == loadingModelId -> ModelCardState.Loading
                    model.id == activeModelId -> ModelCardState.Active
                    model.id in downloadedModelIds -> ModelCardState.Downloaded
                    else -> ModelCardState.NotDownloaded
                }
                ModelCard(
                    model = model,
                    state = state,
                    downloadPercent = downloadPercent,
                    onDownload = { onDownload(model) },
                    onRun = { onRun(model) },
                    onChat = { onChat(model) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: ModelDescriptor,
    state: ModelCardState,
    downloadPercent: Float,
    onDownload: () -> Unit,
    onRun: () -> Unit,
    onChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = model.family,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Spec chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecChip(model.quantization)
            SpecChip("${model.sizeBytes / 1_000_000} MB")
            SpecChip("${model.contextLength} ctx")
            SpecChip("${model.recommendedRamMb} MB RAM")
            if (model.supportsThinking) SpecChip("Thinking")
            if (model.supportsVision) SpecChip("Vision")
        }

        Spacer(Modifier.height(16.dp))

        when (state) {
            ModelCardState.Downloading -> {
                Column {
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Downloading… ${downloadPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ModelCardState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            ModelCardState.Active -> {
                CardButton(
                    text = "Chat",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = onChat,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            }
            ModelCardState.Downloaded -> {
                CardButton(
                    text = "Run",
                    icon = Icons.Default.PlayArrow,
                    onClick = onRun,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            ModelCardState.NotDownloaded -> {
                CardButton(
                    text = "Download",
                    icon = Icons.Default.Download,
                    onClick = onDownload,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CardButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SpecChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
