package dev.edgellm

import dev.edgellm.data.chat.ChatRepository
import dev.edgellm.data.download.ModelDownloader
import dev.edgellm.data.download.OkHttpModelDownloader
import dev.edgellm.data.model.InMemoryInstalledModelRepository
import dev.edgellm.data.model.InstalledModelRepository
import dev.edgellm.data.model.ModelCatalog
import dev.edgellm.data.settings.GenerationSettingsRepository
import dev.edgellm.domain.chat.PromptBuilder
import dev.edgellm.domain.model.ModelDescriptor
import dev.edgellm.domain.templates.ChatTemplateRegistry
import dev.edgellm.engine.InferenceEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppDependencies(
    val engine: InferenceEngine,
    val modelsDir: String,
    val settingsRepository: GenerationSettingsRepository,
    val chatRepository: ChatRepository,
) {
    val chatTemplateRegistry = ChatTemplateRegistry()
    val promptBuilder = PromptBuilder(chatTemplateRegistry)
    val installedModelRepository: InstalledModelRepository = InMemoryInstalledModelRepository()
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val downloader: ModelDownloader = OkHttpModelDownloader(okHttpClient)
    val modelCatalog = ModelCatalog()

    var catalogModels: List<ModelDescriptor> = emptyList()
        private set

    fun loadCatalog(json: String) {
        catalogModels = modelCatalog.parse(json)
    }
}
