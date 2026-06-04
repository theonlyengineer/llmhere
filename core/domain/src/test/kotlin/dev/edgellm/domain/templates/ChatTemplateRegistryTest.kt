package dev.edgellm.domain.templates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatTemplateRegistryTest {

    private val registry = ChatTemplateRegistry()

    @Test
    fun `returns GemmaTemplate for gemma`() {
        val template = registry.templateFor("gemma")
        assertTrue(template is GemmaTemplate)
    }

    @Test
    fun `returns FalconEdgeTemplate for falcon-e`() {
        val template = registry.templateFor("falcon-e")
        assertTrue(template is FalconEdgeTemplate)
    }

    @Test
    fun `returns null for unknown family`() {
        assertNull(registry.templateFor("unknown-model"))
    }

    @Test
    fun `lists supported families`() {
        val families = registry.supportedFamilies()
        assertEquals(setOf("gemma", "falcon-e"), families)
    }
}
