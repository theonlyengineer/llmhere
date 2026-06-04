package dev.edgellm.engine

import kotlinx.coroutines.test.TestScope

class FakeInferenceEngineContractTest : InferenceEngineContractTest() {
    override fun createEngine(scope: TestScope): InferenceEngine =
        FakeInferenceEngine(emits = listOf("Hello", " world"))
}
