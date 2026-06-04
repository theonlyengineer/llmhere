package dev.edgellm.viewmodel

import dev.edgellm.data.chat.ChatRepository
import dev.edgellm.data.chat.ChatSessionManager
import dev.edgellm.data.download.DownloadProgress
import dev.edgellm.data.download.ModelDownloader
import dev.edgellm.data.model.InstalledModelRepository
import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.model.ModelDescriptor
import dev.edgellm.engine.InferenceEngine
import dev.edgellm.engine.LoadConfig
import dev.edgellm.engine.ModelHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ChatUiState {
    data object NoModel : ChatUiState
    data class Downloading(
        val percent: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ChatUiState
    data object LoadingModel : ChatUiState
    data class Ready(
        val messages: List<ChatMessage>,
        val isGenerating: Boolean,
    ) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

class ChatViewModel(
    private val engine: InferenceEngine,
    private val downloader: ModelDownloader,
    private val chatRepository: ChatRepository,
    private val installedModelRepository: InstalledModelRepository,
    private val promptBuilder: PromptBuilder,
    private val modelsDir: String,
    private val scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
) {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.NoModel)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _assistantMessage = MutableStateFlow("")
    val assistantMessage: StateFlow<String> = _assistantMessage

    private var sessionManager: ChatSessionManager? = null
    private var currentDescriptor: ModelDescriptor? = null
    private var downloadJob: Job? = null
    private var sendJob: Job? = null

    fun downloadModel(descriptor: ModelDescriptor) {
        currentDescriptor = descriptor

        // If the model file already exists on disk, skip download and load directly
        val existingFile = File(modelsDir, "${descriptor.id}.gguf")
        if (existingFile.exists() && existingFile.length() > 0) {
            logger("downloadModel: file already exists at ${existingFile.absolutePath} (${existingFile.length()} bytes), loading directly")
            scope.launch {
                loadModel(descriptor, existingFile.absolutePath)
            }
            return
        }

        downloadJob = scope.launch {
            downloader.download(descriptor, modelsDir).onEach { progress ->
                when (progress) {
                    is DownloadProgress.Queued -> {
                        _uiState.value = ChatUiState.Downloading(0f, 0L, descriptor.sizeBytes)
                    }
                    is DownloadProgress.Downloading -> {
                        _uiState.value = ChatUiState.Downloading(
                            progress.percent,
                            progress.bytesDownloaded,
                            progress.totalBytes,
                        )
                    }
                    is DownloadProgress.Verifying -> {
                        // Stay in downloading state during verification
                    }
                    is DownloadProgress.Complete -> {
                        installedModelRepository.markInstalled(descriptor, progress.localPath)
                        loadModel(descriptor, progress.localPath)
                    }
                    is DownloadProgress.Failed -> {
                        _uiState.value = ChatUiState.Error(
                            progress.cause.message ?: "Download failed"
                        )
                    }
                }
            }.collect()
        }
    }

    suspend fun loadModel(descriptor: ModelDescriptor, localPath: String) {
        _uiState.value = ChatUiState.LoadingModel
        currentDescriptor = descriptor

        val file = File(localPath)
        logger("loadModel: path=$localPath exists=${file.exists()} size=${file.length()}")

        val modelHandle = ModelHandle(
            file = file,
            family = descriptor.family,
            quantization = descriptor.quantization,
            contextLength = descriptor.contextLength,
        )

        val result = withContext(Dispatchers.IO) {
            engine.load(
                modelHandle,
                LoadConfig(
                    contextSize = descriptor.contextLength,
                    // Offload all layers to the Adreno GPU via the OpenCL backend.
                    gpuLayers = 99,
                    // Flash attention is not consistently beneficial/supported on
                    // the Adreno OpenCL backend; keep the stable f16 KV path.
                    flashAttention = false,
                    kvCacheType = "f16",
                ),
            )
        }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            logger("loadModel: failed - ${error?.message}")
            _uiState.value = ChatUiState.Error(
                error?.message ?: "Failed to load model"
            )
            return
        }
        logger("loadModel: success")

        val manager = ChatSessionManager(
            chatRepository = chatRepository,
            promptBuilder = promptBuilder,
            engine = engine,
            family = descriptor.family,
            systemPrompt = null,
            contextLength = descriptor.contextLength,
            stopTokens = descriptor.stopTokens,
        )
        sessionManager = manager
        manager.newSession(descriptor.id)

        // Forward assistant message updates
        scope.launch {
            manager.assistantMessage.onEach { _assistantMessage.value = it }.collect()
        }

        _uiState.value = ChatUiState.Ready(
            messages = emptyList(),
            isGenerating = false,
        )
    }

    fun sendMessage(content: String) {
        val manager = sessionManager ?: return
        sendJob = scope.launch {
            _uiState.value = ChatUiState.Ready(
                messages = manager.currentSession.value?.messages ?: emptyList(),
                isGenerating = true,
            )

            manager.sendMessage(content)

            _uiState.value = ChatUiState.Ready(
                messages = manager.currentSession.value?.messages ?: emptyList(),
                isGenerating = false,
            )
        }
    }

    fun cancelGeneration() {
        sessionManager?.cancelGeneration()
    }

    fun retry() {
        val descriptor = currentDescriptor ?: return
        downloadModel(descriptor)
    }
}
