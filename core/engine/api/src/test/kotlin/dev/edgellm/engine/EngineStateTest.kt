package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineStateTest {

    @Test
    fun `idle is a singleton`() {
        assertTrue(EngineState.Idle is EngineState)
        assertEquals(EngineState.Idle, EngineState.Idle)
    }

    @Test
    fun `loading is a singleton`() {
        assertTrue(EngineState.Loading is EngineState)
    }

    @Test
    fun `ready is a singleton`() {
        assertTrue(EngineState.Ready is EngineState)
    }

    @Test
    fun `generating is a singleton`() {
        assertTrue(EngineState.Generating is EngineState)
    }

    @Test
    fun `error carries cause`() {
        val cause = RuntimeException("boom")
        val state = EngineState.Error(cause)
        assertEquals(cause, state.cause)
    }

    @Test
    fun `different states are not equal`() {
        assertNotEquals(EngineState.Idle, EngineState.Ready)
        assertNotEquals(EngineState.Loading, EngineState.Generating)
    }
}
