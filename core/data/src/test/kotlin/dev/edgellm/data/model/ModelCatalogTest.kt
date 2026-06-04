package dev.edgellm.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelCatalogTest {

    private val catalog = ModelCatalog()

    @Test
    fun `parses valid catalog`() {
        val json = javaClass.classLoader!!.getResource("catalog_valid.json")!!.readText()
        val models = catalog.parse(json)
        assertEquals(2, models.size)
        assertEquals("falcon-e-1b-instruct", models[0].id)
        assertEquals("falcon-e", models[0].family)
        assertEquals("Falcon-E 1B Instruct", models[0].displayName)
        assertEquals("i2_s", models[0].quantization)
        assertEquals(2048, models[0].contextLength)
        assertEquals(listOf("<|im_end|>"), models[0].stopTokens)
        assertEquals("falcon-e-3b-instruct", models[1].id)
    }

    @Test
    fun `skips malformed entries`() {
        val json = javaClass.classLoader!!.getResource("catalog_malformed.json")!!.readText()
        val models = catalog.parse(json)
        // Should skip the entry missing required fields, keep the two valid ones
        assertEquals(2, models.size)
        assertEquals("valid-model", models[0].id)
        assertEquals("also-valid", models[1].id)
    }

    @Test
    fun `handles unknown fields gracefully`() {
        val json = javaClass.classLoader!!.getResource("catalog_malformed.json")!!.readText()
        val models = catalog.parse(json)
        val gemma = models.find { it.family == "gemma" }!!
        assertEquals("Q4_K_M", gemma.quantization)
    }

    @Test
    fun `returns empty list for invalid json`() {
        val models = catalog.parse("not json at all")
        assertTrue(models.isEmpty())
    }

    @Test
    fun `returns empty list for empty array`() {
        val models = catalog.parse("[]")
        assertTrue(models.isEmpty())
    }
}
