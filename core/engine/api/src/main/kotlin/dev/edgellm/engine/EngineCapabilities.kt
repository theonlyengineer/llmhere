package dev.edgellm.engine

data class EngineCapabilities(
    val supportsGpu: Boolean,
    val supportedQuantizations: Set<String>,
    val supportedFamilies: Set<String>,
)
