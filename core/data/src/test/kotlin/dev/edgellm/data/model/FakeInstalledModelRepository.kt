package dev.edgellm.data.model

import dev.edgellm.domain.model.InstalledModel
import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeInstalledModelRepository : InstalledModelRepository {

    private val models = MutableStateFlow<Map<String, InstalledModel>>(emptyMap())

    override fun getInstalledModels(): Flow<List<InstalledModel>> =
        models.map { it.values.toList() }

    override suspend fun getInstalledModel(id: String): InstalledModel? =
        models.value[id]

    override suspend fun markInstalled(descriptor: ModelDescriptor, localPath: String): InstalledModel {
        val now = System.currentTimeMillis()
        val model = InstalledModel(
            descriptor = descriptor,
            localPath = localPath,
            downloadedAt = now,
            lastUsedAt = now,
            verified = true,
        )
        models.value = models.value + (descriptor.id to model)
        return model
    }

    override suspend fun removeModel(id: String) {
        models.value = models.value - id
    }

    override suspend fun isInstalled(id: String): Boolean =
        models.value.containsKey(id)
}
