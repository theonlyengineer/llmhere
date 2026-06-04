package dev.edgellm.data.download

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.Flow

interface ModelDownloader {
    fun download(descriptor: ModelDescriptor, destDir: String): Flow<DownloadProgress>
    fun cancel(modelId: String)
    fun isDownloading(modelId: String): Boolean
}
