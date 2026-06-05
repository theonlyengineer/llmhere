package dev.edgellm.data.download

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.Flow

interface DownloadManager {
    fun enqueue(descriptor: ModelDescriptor)
    fun cancel(modelId: String)
    fun observeProgress(modelId: String): Flow<DownloadProgress>
}
