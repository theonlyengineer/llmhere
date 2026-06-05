package dev.edgellm.data.download

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDownloadManager : DownloadManager {

    private val progressFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()
    val enqueuedModels = mutableListOf<String>()
    val cancelledModels = mutableListOf<String>()

    override fun enqueue(descriptor: ModelDescriptor) {
        enqueuedModels.add(descriptor.id)
        progressFlows[descriptor.id] = MutableStateFlow(DownloadProgress.Queued)
    }

    override fun cancel(modelId: String) {
        cancelledModels.add(modelId)
    }

    override fun observeProgress(modelId: String): Flow<DownloadProgress> {
        return progressFlows.getOrPut(modelId) { MutableStateFlow(DownloadProgress.Queued) }
    }

    fun emitProgress(modelId: String, progress: DownloadProgress) {
        progressFlows[modelId]?.value = progress
    }
}
