package dev.edgellm.viewmodel

import dev.edgellm.data.chat.ChatRepository
import dev.edgellm.data.chat.ChatSessionManager
import dev.edgellm.data.download.DownloadProgress
import dev.edgellm.data.download.ModelDownloader
import dev.edgellm.data.model.InstalledModelRepository
import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.model.ModelDescriptor
import dev.edgellm.engine.GenerationConfig
import dev.edgellm.engine.InferenceEngine
import dev.edgellm.engine.LoadConfig
import dev.edgellm.engine.ModelHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.NoModel)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _assistantMessage = MutableStateFlow("")
    val assistantMessage: StateFlow<String> = _assistantMessage

    private val _currentDescriptor = MutableStateFlow<ModelDescriptor?>(null)
    val currentDescriptor: StateFlow<ModelDescriptor?> = _currentDescriptor

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of models whose .gguf file is present on disk. */
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds

    /** Stream of all persisted chat sessions, most-recently-updated first. */
    fun sessions(): Flow<List<ChatSession>> = chatRepository.getSessions()

    /** Re-scan [modelsDir] for downloaded .gguf model files. */
    fun refreshDownloadedModels() {
        val dir = File(modelsDir)
        _downloadedModelIds.value = dir.listFiles()
            ?.filter { it.isFile && it.extension == "gguf" && it.length() > 0 }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
    }

    var catalogModels: List<ModelDescriptor> = emptyList()

    private var sessionManager: ChatSessionManager? = null
    private var downloadJob: Job? = null
    private var sendJob: Job? = null

    private var currentSystemPrompt: String =
        "You are a helpful assistant. Answer questions clearly and concisely."
    private var currentGenerationConfig: GenerationConfig = GenerationConfig()
    private var currentHistoryTurns: Int = 3

    fun downloadModel(descriptor: ModelDescriptor) {
        _currentDescriptor.value = descriptor

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
        _currentDescriptor.value = descriptor

        val file = File(localPath)
        logger("loadModel: path=$localPath exists=${file.exists()} size=${file.length()}")

        val modelHandle = ModelHandle(
            file = file,
            family = descriptor.family,
            quantization = descriptor.quantization,
            contextLength = descriptor.contextLength,
        )

        val result = withContext(ioDispatcher) {
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
            systemPrompt = currentSystemPrompt,
            contextLength = descriptor.contextLength,
            stopTokens = descriptor.stopTokens,
            generationConfig = currentGenerationConfig,
            historyTurns = currentHistoryTurns,
        )
        sessionManager = manager

        // Resume an existing session so history survives model loads/switches and
        // process restarts. Only create a fresh session when there is nothing to resume.
        val resumeId = _currentSessionId.value
        when {
            resumeId != null && chatRepository.getSession(resumeId) != null -> {
                manager.switchSession(resumeId)
            }
            else -> {
                val latest = chatRepository.getSessions().first().firstOrNull()
                if (latest != null) {
                    manager.switchSession(latest.id)
                    _currentSessionId.value = latest.id
                } else {
                    val created = manager.newSession(descriptor.id)
                    _currentSessionId.value = created.id
                }
            }
        }

        // Forward assistant message updates
        scope.launch {
            manager.assistantMessage.onEach { _assistantMessage.value = it }.collect()
        }

        _uiState.value = ChatUiState.Ready(
            messages = manager.currentSession.value?.messages ?: emptyList(),
            isGenerating = false,
        )
        refreshDownloadedModels()
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

    /** Start a fresh conversation. Reuses the current session if it is already empty. */
    fun newChat() {
        val manager = sessionManager ?: return
        val descriptor = _currentDescriptor.value ?: return
        scope.launch {
            cancelGeneration()
            val current = manager.currentSession.value
            if (current == null || current.messages.isNotEmpty()) {
                val created = manager.newSession(descriptor.id)
                _currentSessionId.value = created.id
            }
            _assistantMessage.value = ""
            _uiState.value = ChatUiState.Ready(messages = emptyList(), isGenerating = false)
        }
    }

    /** Open a previous conversation from history; its messages are restored on screen. */
    fun openSession(id: String) {
        val manager = sessionManager ?: return
        scope.launch {
            cancelGeneration()
            manager.switchSession(id)
            _currentSessionId.value = id
            _assistantMessage.value = ""
            _uiState.value = ChatUiState.Ready(
                messages = manager.currentSession.value?.messages ?: emptyList(),
                isGenerating = false,
            )
        }
    }

    fun deleteSession(id: String) {
        scope.launch {
            chatRepository.deleteSession(id)
            if (_currentSessionId.value == id) {
                // The active chat was deleted — start a fresh one.
                val manager = sessionManager
                val descriptor = _currentDescriptor.value
                if (manager != null && descriptor != null) {
                    val created = manager.newSession(descriptor.id)
                    _currentSessionId.value = created.id
                    _assistantMessage.value = ""
                    _uiState.value = ChatUiState.Ready(messages = emptyList(), isGenerating = false)
                }
            }
        }
    }

    fun retry() {
        val descriptor = _currentDescriptor.value ?: return
        downloadModel(descriptor)
    }

    fun switchModel(descriptor: ModelDescriptor) {
        cancelGeneration()
        scope.launch {
            engine.unload()
            val localFile = File(modelsDir, "${descriptor.id}.gguf")
            if (localFile.exists() && localFile.length() > 0) {
                loadModel(descriptor, localFile.absolutePath)
            } else {
                downloadModel(descriptor)
            }
        }
    }

    fun restartModel() {
        val descriptor = _currentDescriptor.value ?: return
        val localFile = File(modelsDir, "${descriptor.id}.gguf")
        if (!localFile.exists()) return
        scope.launch {
            cancelGeneration()
            engine.unload()
            loadModel(descriptor, localFile.absolutePath)
        }
    }

    fun applySettings(
        systemPrompt: String = currentSystemPrompt,
        temperature: Float = currentGenerationConfig.temperature,
        repeatPenalty: Float = currentGenerationConfig.repeatPenalty,
        maxTokens: Int = currentGenerationConfig.maxTokens,
        historyTurns: Int = currentHistoryTurns,
    ) {
        currentSystemPrompt = systemPrompt
        currentGenerationConfig = GenerationConfig(
            temperature = temperature,
            repeatPenalty = repeatPenalty,
            maxTokens = maxTokens,
        )
        currentHistoryTurns = historyTurns
        // Update settings in-place on the existing session manager (no session restart needed)
        sessionManager?.let { manager ->
            manager.systemPrompt = currentSystemPrompt
            manager.generationConfig = currentGenerationConfig
            manager.historyTurns = currentHistoryTurns
        }
    }
}
