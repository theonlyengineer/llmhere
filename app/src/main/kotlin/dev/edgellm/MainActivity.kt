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
import dev.edgellm.ui.screens.HomeScreen
import dev.edgellm.ui.screens.SettingsScreen
import dev.edgellm.ui.theme.EdgeLlmTheme
import dev.edgellm.viewmodel.ChatUiState
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

        viewModel.refreshDownloadedModels()

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
                    historyTurns = settings.historyTurns,
                )
            }
        }

        setContent {
            EdgeLlmTheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsState()
                val assistantMessage by viewModel.assistantMessage.collectAsState()
                val currentModel by viewModel.currentDescriptor.collectAsState()
                val currentSessionId by viewModel.currentSessionId.collectAsState()
                val sessions by viewModel.sessions().collectAsState(initial = emptyList())
                val downloadedIds by viewModel.downloadedModelIds.collectAsState()
                val settings by settingsFlow.collectAsState()

                val defaultModel = deps.catalogModels.firstOrNull()

                // Derive per-model status hints for the Home gallery.
                val activeModelId = if (uiState is ChatUiState.Ready) currentModel?.id else null
                val loadingModelId = if (uiState is ChatUiState.LoadingModel) currentModel?.id else null
                val downloadingModelId = if (uiState is ChatUiState.Downloading) currentModel?.id else null
                val downloadPercent = (uiState as? ChatUiState.Downloading)?.percent ?: 0f

                NavHost(navController = navController, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            models = deps.catalogModels,
                            downloadedModelIds = downloadedIds,
                            activeModelId = activeModelId,
                            downloadingModelId = downloadingModelId,
                            downloadPercent = downloadPercent,
                            loadingModelId = loadingModelId,
                            onDownload = { model ->
                                viewModel.downloadModel(model)
                                navController.navigate(Screen.Chat.route)
                            },
                            onRun = { model ->
                                viewModel.switchModel(model)
                                navController.navigate(Screen.Chat.route)
                            },
                            onChat = { navController.navigate(Screen.Chat.route) },
                        )
                    }
                    composable(Screen.Chat.route) {
                        ChatScreen(
                            uiState = uiState,
                            assistantMessage = assistantMessage,
                            currentModel = currentModel,
                            catalogModels = deps.catalogModels,
                            downloadedModelIds = downloadedIds,
                            sessions = sessions,
                            currentSessionId = currentSessionId,
                            onDownloadClick = { defaultModel?.let { viewModel.downloadModel(it) } },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onCancelGeneration = { viewModel.cancelGeneration() },
                            onRetry = { viewModel.retry() },
                            onSelectModel = { viewModel.switchModel(it) },
                            onRestartModel = { viewModel.restartModel() },
                            onOpenSettings = { navController.navigate(Screen.Settings.route) },
                            onNewChat = { viewModel.newChat() },
                            onOpenSession = { viewModel.openSession(it) },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onBackToHome = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
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

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refreshDownloadedModels()
    }
}
