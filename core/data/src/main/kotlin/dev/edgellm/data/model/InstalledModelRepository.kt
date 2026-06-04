package dev.edgellm.data.model

import dev.edgellm.domain.model.InstalledModel
import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.Flow

interface InstalledModelRepository {
    fun getInstalledModels(): Flow<List<InstalledModel>>
    suspend fun getInstalledModel(id: String): InstalledModel?
    suspend fun markInstalled(descriptor: ModelDescriptor, localPath: String): InstalledModel
    suspend fun removeModel(id: String)
    suspend fun isInstalled(id: String): Boolean
}
