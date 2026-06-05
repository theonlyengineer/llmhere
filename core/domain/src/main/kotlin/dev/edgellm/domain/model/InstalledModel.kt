package dev.edgellm.domain.model

data class InstalledModel(
    val descriptor: ModelDescriptor,
    val localPath: String,
    val downloadedAt: Long,
    val lastUsedAt: Long,
    val verified: Boolean,
)
