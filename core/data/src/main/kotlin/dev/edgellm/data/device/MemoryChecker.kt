package dev.edgellm.data.device

import dev.edgellm.domain.model.ModelDescriptor

sealed interface MemoryCheckResult {
    data object Ok : MemoryCheckResult
    data class LowRam(val available: Int, val recommended: Int) : MemoryCheckResult
    data class InsufficientRam(val available: Int, val minimum: Int) : MemoryCheckResult
}

interface MemoryChecker {
    fun availableRamMb(): Int
    fun canLoadModel(descriptor: ModelDescriptor): MemoryCheckResult
}
