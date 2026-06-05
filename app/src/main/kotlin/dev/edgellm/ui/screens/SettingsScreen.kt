package dev.edgellm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.edgellm.data.settings.GenerationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: GenerationSettings,
    showThinkingToggle: Boolean,
    onSave: (GenerationSettings) -> Unit,
    onBack: () -> Unit,
) {
    var temperature by remember { mutableFloatStateOf(settings.temperature) }
    var repeatPenalty by remember { mutableFloatStateOf(settings.repeatPenalty) }
    var maxTokens by remember { mutableFloatStateOf(settings.maxTokens.toFloat()) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var thinkingEnabled by remember { mutableStateOf(settings.thinkingEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Generation")

            SliderSetting(
                label = "Temperature",
                value = temperature,
                valueLabel = "%.2f".format(temperature),
                range = 0f..1.5f,
                onChange = { temperature = it },
            )
            SliderSetting(
                label = "Repeat penalty",
                value = repeatPenalty,
                valueLabel = "%.2f".format(repeatPenalty),
                range = 1.0f..2.0f,
                onChange = { repeatPenalty = it },
            )
            SliderSetting(
                label = "Max tokens",
                value = maxTokens,
                valueLabel = maxTokens.toInt().toString(),
                range = 16f..512f,
                onChange = { maxTokens = it },
            )

            if (showThinkingToggle) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Thinking mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Show step-by-step reasoning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("System prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(
                        GenerationSettings(
                            temperature = temperature,
                            repeatPenalty = repeatPenalty,
                            maxTokens = maxTokens.toInt(),
                            systemPrompt = systemPrompt,
                            thinkingEnabled = thinkingEnabled,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
