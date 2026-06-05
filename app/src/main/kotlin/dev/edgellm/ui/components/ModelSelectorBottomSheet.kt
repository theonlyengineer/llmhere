package dev.edgellm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edgellm.domain.model.ModelDescriptor

/**
 * Bottom sheet listing models grouped into: currently loaded, downloaded (switchable),
 * and available (downloadable from catalog). Mirrors Google AI Edge Gallery's model picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorBottomSheet(
    currentModelId: String?,
    catalogModels: List<ModelDescriptor>,
    downloadedModelIds: Set<String>,
    onSelectModel: (ModelDescriptor) -> Unit,
    onRestartModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val loaded = catalogModels.filter { it.id == currentModelId }
    val downloaded = catalogModels.filter { it.id != currentModelId && it.id in downloadedModelIds }
    val available = catalogModels.filter { it.id != currentModelId && it.id !in downloadedModelIds }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            if (loaded.isNotEmpty()) {
                SectionLabel("Loaded")
                loaded.forEach { model ->
                    ModelRow(
                        model = model,
                        isCurrent = true,
                        isDownloaded = true,
                        onClick = {},
                        trailing = {
                            TextButton(onClick = { onRestartModel(); onDismiss() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Restart")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (downloaded.isNotEmpty()) {
                SectionLabel("Downloaded")
                downloaded.forEach { model ->
                    ModelRow(
                        model = model,
                        isCurrent = false,
                        isDownloaded = true,
                        onClick = { onSelectModel(model); onDismiss() },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (available.isNotEmpty()) {
                SectionLabel("Available")
                available.forEach { model ->
                    ModelRow(
                        model = model,
                        isCurrent = false,
                        isDownloaded = false,
                        onClick = { onSelectModel(model); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun ModelRow(
    model: ModelDescriptor,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val borderColor =
        if (isCurrent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuantBadge(model.quantization)
                Text(
                    text = "${model.sizeBytes / 1_000_000} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun QuantBadge(quantization: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = quantization,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
