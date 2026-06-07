package dev.edgellm.data.settings

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerationSettingsRepositoryTest {

    private val repo = FakeGenerationSettingsRepository()

    @Test
    fun `defaults are correct`() = runTest {
        repo.getSettings().test {
            val s = awaitItem()
            assertEquals(0.3f, s.temperature)
            assertEquals(1.3f, s.repeatPenalty)
            assertEquals(512, s.maxTokens)
            assertEquals(false, s.thinkingEnabled)
        }
    }

    @Test
    fun `update persists new values`() = runTest {
        val updated = GenerationSettings(temperature = 0.7f, maxTokens = 200)
        repo.update(updated)
        repo.getSettings().test {
            val s = awaitItem()
            assertEquals(0.7f, s.temperature)
            assertEquals(200, s.maxTokens)
        }
    }

    @Test
    fun `update emits new value to existing collectors`() = runTest {
        repo.getSettings().test {
            awaitItem() // initial defaults

            repo.update(GenerationSettings(temperature = 0.5f))

            val updated = awaitItem()
            assertEquals(0.5f, updated.temperature)
        }
    }
}
