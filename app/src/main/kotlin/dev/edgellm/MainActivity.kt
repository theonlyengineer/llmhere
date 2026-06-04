package dev.edgellm

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dev.edgellm.ui.screens.ChatScreen
import dev.edgellm.ui.theme.EdgeLlmTheme
import dev.edgellm.viewmodel.ChatViewModel

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
        )

        setContent {
            EdgeLlmTheme {
                val uiState by viewModel.uiState.collectAsState()
                val assistantMessage by viewModel.assistantMessage.collectAsState()

                val defaultModel = deps.catalogModels.firstOrNull()

                ChatScreen(
                    uiState = uiState,
                    assistantMessage = assistantMessage,
                    onDownloadClick = {
                        defaultModel?.let { viewModel.downloadModel(it) }
                    },
                    onSendMessage = { viewModel.sendMessage(it) },
                    onCancelGeneration = { viewModel.cancelGeneration() },
                    onRetry = { viewModel.retry() },
                )
            }
        }
    }
}
