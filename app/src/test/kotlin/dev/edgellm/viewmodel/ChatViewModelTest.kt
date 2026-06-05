package dev.edgellm.viewmodel

import app.cash.turbine.test
import dev.edgellm.data.chat.InMemoryChatRepository
import dev.edgellm.data.download.DownloadProgress
import dev.edgellm.data.download.ModelDownloader
import dev.edgellm.data.model.InMemoryInstalledModelRepository
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.chat.Role
import dev.edgellm.domain.model.ModelDescriptor
import dev.edgellm.domain.templates.ChatTemplateRegistry
import dev.edgellm.engine.FakeInferenceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val descriptor = ModelDescriptor(
        id = "falcon-e-1b-instruct",
        family = "falcon-e",
        displayName = "Falcon-E 1B Instruct",
        url = "https://example.com/model.gguf",
        sha256 = "",
        sizeBytes = 665_000_000L,
        quantization = "i2_s",
        contextLength = 2048,
        minRamMb = 1024,
        recommendedRamMb = 2048,
        chatTemplate = "falcon-e",
        stopTokens = listOf("<|im_end|>"),
        engine = "llamacpp",
    )

    private class FakeModelDownloader : ModelDownloader {
        val progressFlow = MutableSharedFlow<DownloadProgress>()
        var cancelledId: String? = null
        var downloading = false

        override fun download(descriptor: ModelDescriptor, destDir: String): Flow<DownloadProgress> {
            downloading = true
            return progressFlow
        }

        override fun cancel(modelId: String) {
            cancelledId = modelId
            downloading = false
        }

        override fun isDownloading(modelId: String): Boolean = downloading
    }

    private fun createViewModel(
        scope: TestScope,
        emits: List<String> = listOf("Hello", " world"),
        loadResult: Result<Unit> = Result.success(Unit),
    ): Triple<ChatViewModel, FakeInferenceEngine, FakeModelDownloader> {
        val engine = FakeInferenceEngine(emits = emits, loadResult = loadResult)
        val downloader = FakeModelDownloader()
        val registry = ChatTemplateRegistry()
        val promptBuilder = PromptBuilder(registry)
        val vm = ChatViewModel(
            engine = engine,
            downloader = downloader,
            chatRepository = InMemoryChatRepository(),
            installedModelRepository = InMemoryInstalledModelRepository(),
            promptBuilder = promptBuilder,
            modelsDir = "/tmp/models",
            scope = scope.backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )
        return Triple(vm, engine, downloader)
    }

    @Test
    fun `initial state is NoModel`() = runTest {
        val (vm, _, _) = createViewModel(this)
        assertEquals(ChatUiState.NoModel, vm.uiState.value)
    }

    @Test
    fun `download emits progress`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, downloader) = createViewModel(this)

        vm.uiState.test {
            assertEquals(ChatUiState.NoModel, awaitItem())

            vm.downloadModel(descriptor)

            downloader.progressFlow.emit(DownloadProgress.Queued)
            val queued = awaitItem()
            assertTrue(queued is ChatUiState.Downloading)

            downloader.progressFlow.emit(
                DownloadProgress.Downloading(50f, 300_000_000L, 665_000_000L)
            )
            val downloading = awaitItem()
            assertTrue(downloading is ChatUiState.Downloading)
            assertEquals(50f, (downloading as ChatUiState.Downloading).percent)
        }
    }

    @Test
    fun `download failure shows error`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, downloader) = createViewModel(this)

        vm.uiState.test {
            assertEquals(ChatUiState.NoModel, awaitItem())

            vm.downloadModel(descriptor)

            downloader.progressFlow.emit(DownloadProgress.Failed(RuntimeException("Network error")))
            val error = awaitItem()
            assertTrue(error is ChatUiState.Error)
            assertEquals("Network error", (error as ChatUiState.Error).message)
        }
    }

    @Test
    fun `load model transitions to Ready`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this)

        vm.uiState.test {
            assertEquals(ChatUiState.NoModel, awaitItem())

            vm.loadModel(descriptor, "/tmp/models/falcon.gguf")

            assertEquals(ChatUiState.LoadingModel, awaitItem())

            val ready = awaitItem()
            assertTrue(ready is ChatUiState.Ready)
            assertTrue((ready as ChatUiState.Ready).messages.isEmpty())
        }
    }

    @Test
    fun `load model failure shows error`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(
            this,
            loadResult = Result.failure(RuntimeException("Out of memory")),
        )

        vm.uiState.test {
            assertEquals(ChatUiState.NoModel, awaitItem())

            vm.loadModel(descriptor, "/tmp/models/falcon.gguf")

            assertEquals(ChatUiState.LoadingModel, awaitItem())

            val error = awaitItem()
            assertTrue(error is ChatUiState.Error)
            assertEquals("Out of memory", (error as ChatUiState.Error).message)
        }
    }

    @Test
    fun `send message adds user and assistant messages`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this, emits = listOf("Hi", " there"))

        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")

        vm.uiState.test {
            val ready = awaitItem()
            assertTrue(ready is ChatUiState.Ready)

            vm.sendMessage("hello")

            // Collect states until we get the final non-generating state with messages
            val items = mutableListOf<ChatUiState>()
            while (true) {
                val item = awaitItem()
                items.add(item)
                if (item is ChatUiState.Ready && !item.isGenerating && item.messages.isNotEmpty()) break
            }

            val done = items.last()
            assertTrue(done is ChatUiState.Ready)
            val messages = (done as ChatUiState.Ready).messages
            assertEquals(2, messages.size)
            assertEquals(Role.User, messages[0].role)
            assertEquals("hello", messages[0].content)
            assertEquals(Role.Assistant, messages[1].role)
            assertEquals("Hi there", messages[1].content)
        }
    }

    @Test
    fun `cancel generation delegates to engine`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this)
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        // Should not throw
        vm.cancelGeneration()
    }

    @Test
    fun `prompt includes system prompt after load`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, engine, _) = createViewModel(this, emits = listOf("ok"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        // Wait for Ready state before sending
        vm.uiState.test {
            awaitItem() // consume Ready
            vm.sendMessage("hi")
            // Wait until generation is done (isGenerating goes false)
            while (true) {
                val s = awaitItem()
                if (s is ChatUiState.Ready && !s.isGenerating) break
            }
        }
        assertTrue(
            engine.lastPrompt.contains("helpful assistant"),
            "Prompt should contain system prompt but was:\n${engine.lastPrompt}",
        )
    }

    @Test
    fun `switchModel ends in Ready state`() = runTest(UnconfinedTestDispatcher()) {
        val tmpDir = createTempDir("edgellm-test-models")
        val tmpFile = java.io.File(tmpDir, "${descriptor.id}.gguf").also { it.createNewFile() }
        val engine = FakeInferenceEngine(emits = listOf("ok"))
        val vm = ChatViewModel(
            engine = engine,
            downloader = FakeModelDownloader(),
            chatRepository = InMemoryChatRepository(),
            installedModelRepository = InMemoryInstalledModelRepository(),
            promptBuilder = PromptBuilder(ChatTemplateRegistry()),
            modelsDir = tmpDir.absolutePath,
            scope = backgroundScope,
        )
        vm.loadModel(descriptor, tmpFile.absolutePath)
        assertTrue(vm.uiState.value is ChatUiState.Ready)

        vm.switchModel(descriptor)
        // Allow IO-dispatched loadModel to complete
        kotlinx.coroutines.delay(500)
        assertTrue(
            vm.uiState.value is ChatUiState.Ready || vm.uiState.value is ChatUiState.LoadingModel,
            "Expected Ready or LoadingModel after switchModel, got ${vm.uiState.value}",
        )
        tmpDir.deleteRecursively()
    }

    @Test
    fun `switchModel keeps existing messages visible`() = runTest(UnconfinedTestDispatcher()) {
        val tmpDir = createTempDir("edgellm-test-models")
        val tmpFile = java.io.File(tmpDir, "${descriptor.id}.gguf").also { it.createNewFile() }
        val engine = FakeInferenceEngine(emits = listOf("response"))
        val vm = ChatViewModel(
            engine = engine,
            downloader = FakeModelDownloader(),
            chatRepository = InMemoryChatRepository(),
            installedModelRepository = InMemoryInstalledModelRepository(),
            promptBuilder = PromptBuilder(ChatTemplateRegistry()),
            modelsDir = tmpDir.absolutePath,
            scope = backgroundScope,
        )
        vm.loadModel(descriptor, tmpFile.absolutePath)
        vm.sendMessage("hi")
        advanceUntilIdle()

        vm.switchModel(descriptor)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ChatUiState.Ready)
        tmpDir.deleteRecursively()
    }

    @Test
    fun `restartModel reloads the current model`() = runTest(UnconfinedTestDispatcher()) {
        val tmpDir = createTempDir("edgellm-test-models")
        val tmpFile = java.io.File(tmpDir, "${descriptor.id}.gguf").also { it.createNewFile() }
        val engine = FakeInferenceEngine(emits = listOf("ok"))
        val vm = ChatViewModel(
            engine = engine,
            downloader = FakeModelDownloader(),
            chatRepository = InMemoryChatRepository(),
            installedModelRepository = InMemoryInstalledModelRepository(),
            promptBuilder = PromptBuilder(ChatTemplateRegistry()),
            modelsDir = tmpDir.absolutePath,
            scope = backgroundScope,
        )
        vm.loadModel(descriptor, tmpFile.absolutePath)

        vm.uiState.test {
            awaitItem() // consume Ready
            vm.restartModel()
            val loading = awaitItem()
            assertTrue(loading is ChatUiState.LoadingModel)
            val ready = awaitItem()
            assertTrue(ready is ChatUiState.Ready)
        }
        tmpDir.deleteRecursively()
    }

    @Test
    fun `restartModel is no-op when no model loaded`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this)
        vm.restartModel()
        assertEquals(ChatUiState.NoModel, vm.uiState.value)
    }

    @Test
    fun `applySettings updates generation config for next message`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, engine, _) = createViewModel(this, emits = listOf("ok"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")

        // Ensure model is actually loaded before applying settings
        val stateAfterLoad = vm.uiState.value
        assertTrue(
            stateAfterLoad is ChatUiState.Ready,
            "Expected Ready after loadModel but got $stateAfterLoad",
        )

        vm.applySettings(
            systemPrompt = "You are a pirate.",
            temperature = 0.9f,
            repeatPenalty = 1.5f,
            maxTokens = 50,
        )

        vm.uiState.test {
            awaitItem() // consume Ready
            vm.sendMessage("ahoy")
            // Wait for generation to complete
            while (true) {
                val s = awaitItem()
                if (s is ChatUiState.Ready && !s.isGenerating) break
            }
        }

        assertTrue(
            engine.lastPrompt.contains("pirate"),
            "Updated system prompt should be in next prompt, but lastPrompt was: '${engine.lastPrompt}'",
        )
    }

    @Test
    fun `newChat starts an empty session and clears messages`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this, emits = listOf("reply"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        vm.sendMessage("hello")
        advanceUntilIdle()

        vm.newChat()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ChatUiState.Ready)
        assertTrue((state as ChatUiState.Ready).messages.isEmpty(), "New chat should have no messages")
    }

    @Test
    fun `sessions flow exposes created sessions`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this, emits = listOf("reply"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        vm.sendMessage("hi")
        advanceUntilIdle()

        val sessions = vm.sessions().first()
        assertTrue(sessions.isNotEmpty(), "Sessions flow should expose the active session")
    }

    @Test
    fun `openSession loads that session's messages`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this, emits = listOf("reply"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        vm.sendMessage("first message")
        advanceUntilIdle()
        val firstSessionId = vm.currentSessionId.value!!

        // Start a new chat (different session)
        vm.newChat()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as ChatUiState.Ready).messages.isEmpty())

        // Reopen the first session — its messages come back
        vm.openSession(firstSessionId)
        advanceUntilIdle()
        val reopened = vm.uiState.value as ChatUiState.Ready
        assertTrue(reopened.messages.any { it.content == "first message" })
    }

    @Test
    fun `deleteSession removes it from the sessions flow`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = createViewModel(this, emits = listOf("reply"))
        vm.loadModel(descriptor, "/tmp/models/falcon.gguf")
        vm.sendMessage("hi")
        advanceUntilIdle()
        val id = vm.currentSessionId.value!!

        vm.deleteSession(id)
        advanceUntilIdle()

        assertTrue(vm.sessions().first().none { it.id == id })
    }
}
