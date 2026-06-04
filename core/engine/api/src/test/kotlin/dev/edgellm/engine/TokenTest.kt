package dev.edgellm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `default values`() {
        val token = Token(text = "hello")
        assertEquals("hello", token.text)
        assertNull(token.logprob)
        assertFalse(token.isFinal)
    }

    @Test
    fun `all fields set`() {
        val token = Token(text = "world", logprob = -0.5f, isFinal = true)
        assertEquals("world", token.text)
        assertEquals(-0.5f, token.logprob)
        assertTrue(token.isFinal)
    }

    @Test
    fun `data class equality`() {
        val a = Token(text = "x", logprob = 1.0f, isFinal = false)
        val b = Token(text = "x", logprob = 1.0f, isFinal = false)
        assertEquals(a, b)
    }
}
