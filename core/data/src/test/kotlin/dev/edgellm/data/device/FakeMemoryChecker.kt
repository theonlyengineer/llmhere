package dev.edgellm.data.device

import dev.edgellm.domain.model.ModelDescriptor

class FakeMemoryChecker(private val availableRamMb: Int) : MemoryChecker {

    override fun availableRamMb(): Int = availableRamMb

    override fun canLoadModel(descriptor: ModelDescriptor): MemoryCheckResult {
        return when {
            availableRamMb < descriptor.minRamMb ->
                MemoryCheckResult.InsufficientRam(availableRamMb, descriptor.minRamMb)
            availableRamMb < descriptor.recommendedRamMb ->
                MemoryCheckResult.LowRam(availableRamMb, descriptor.recommendedRamMb)
            else -> MemoryCheckResult.Ok
        }
    }
}
