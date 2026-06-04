package dev.edgellm.engine.llamacpp

import dev.edgellm.engine.InferenceEngine
import dev.edgellm.engine.InferenceEngineContractTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class LlamaCppEngineContractTest : InferenceEngineContractTest() {

    override fun createEngine(scope: TestScope): InferenceEngine {
        val bindings = FakeNativeBindings(tokens = listOf("Hello", " world"))
        return LlamaCppEngine(bindings, UnconfinedTestDispatcher(scope.testScheduler))
    }
}
