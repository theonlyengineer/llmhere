package dev.edgellm

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.edgellm.data.settings.GenerationSettings
import dev.edgellm.ui.navigation.Screen
import dev.edgellm.ui.screens.ChatScreen
import dev.edgellm.ui.screens.SettingsScreen
import dev.edgellm.ui.theme.EdgeLlmTheme
import dev.edgellm.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as EdgeLlmApplication
        val deps = app.dependencies

        viewModel = ChatViewModel(
            engine = deps.engine,
            downloader = deps.downloader,
            chatRepository = deps.chatRepository,
            installedModelRepository = deps.installedModelRepository,
            promptBuilder = deps.promptBuilder,
            modelsDir = deps.modelsDir,
            scope = lifecycleScope,
            logger = { Log.i("EdgeLLM", it) },
        ).also { it.catalogModels = deps.catalogModels }

        // Apply persisted settings to the view model on startup.
        val settingsFlow = MutableStateFlow(GenerationSettings())
        lifecycleScope.launch {
            deps.settingsRepository.getSettings().collect { settings ->
                settingsFlow.value = settings
                viewModel.applySettings(
                    systemPrompt = settings.systemPrompt,
                    temperature = settings.temperature,
                    repeatPenalty = settings.repeatPenalty,
                    maxTokens = settings.maxTokens,
                )
            }
        }

        setContent {
            EdgeLlmTheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsState()
                val assistantMessage by viewModel.assistantMessage.collectAsState()
                val currentModel by viewModel.currentDescriptor.collectAsState()
                val installedModels by deps.installedModelRepository.getInstalledModels()
                    .collectAsState(initial = emptyList())
                val settings by settingsFlow.collectAsState()

                val defaultModel = deps.catalogModels.firstOrNull()
                val downloadedIds = installedModels.map { it.descriptor.id }.toSet()

                NavHost(navController = navController, startDestination = Screen.Chat.route) {
                    composable(Screen.Chat.route) {
                        ChatScreen(
                            uiState = uiState,
                            assistantMessage = assistantMessage,
                            currentModel = currentModel,
                            catalogModels = deps.catalogModels,
                            downloadedModelIds = downloadedIds,
                            onDownloadClick = { defaultModel?.let { viewModel.downloadModel(it) } },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onCancelGeneration = { viewModel.cancelGeneration() },
                            onRetry = { viewModel.retry() },
                            onSelectModel = { viewModel.switchModel(it) },
                            onRestartModel = { viewModel.restartModel() },
                            onOpenSettings = { navController.navigate(Screen.Settings.route) },
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            settings = settings,
                            showThinkingToggle = currentModel?.supportsThinking == true,
                            onSave = { newSettings ->
                                lifecycleScope.launch {
                                    deps.settingsRepository.update(newSettings)
                                }
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
