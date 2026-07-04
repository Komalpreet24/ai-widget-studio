package com.example.aiwidgetstudio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiOutputExtractorTest {

    @Test
    fun stripMarkdownFences_removesCodeBlock() {
        val raw = """
            ```json
            {"dslVersion":1}
            ```
        """.trimIndent()
        assertEquals("""{"dslVersion":1}""", AiOutputExtractor.stripMarkdownFences(raw))
    }

    @Test
    fun extractBalancedJsonObject_returnsTopLevelObject() {
        val raw = "Here you go:\n{\"dslVersion\":1,\"metadata\":{\"name\":\"X\"}}"
        val json = AiOutputExtractor.extractBalancedJsonObject(raw)
        assertNotNull(json)
        assertEquals("""{"dslVersion":1,"metadata":{"name":"X"}}""", json)
    }
}
