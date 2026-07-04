package com.example.aiwidgetstudio.engine

import com.example.aiwidgetstudio.engine.parser.WidgetDslParser
import com.example.aiwidgetstudio.engine.parser.mapper.WidgetDslMapper
import com.example.aiwidgetstudio.engine.validator.WidgetValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDslProcessorTest {

    private val processor = WidgetDslProcessor(
        parser = WidgetDslParser(),
        mapper = WidgetDslMapper(),
        validator = WidgetValidator()
    )

    @Test
    fun process_rejectsUnsupportedDslVersion() {
        val result = processor.process("""{"dslVersion":2,"metadata":{"name":"X"},"data":{},"actions":[],"ui":{"type":"TEXT","value":"Hi"}}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun process_acceptsValidWaterTracker() {
        val result = processor.process(sampleDsl())
        assertTrue(result.isSuccess)
        assertEquals("Water Tracker", result.getOrNull()?.definition?.metadata?.name)
    }

    @Test
    fun process_returnsWarningsForDuplicateVariables() {
        val dsl = """
            {
              "dslVersion": 1,
              "metadata": { "name": "Dup" },
              "data": {
                "variables": [
                  { "name": "count", "type": "INT", "default": 0 },
                  { "name": "count", "type": "INT", "default": 1 }
                ]
              },
              "actions": [],
              "ui": { "type": "TEXT", "value": "x" }
            }
        """.trimIndent()
        val warnings = processor.process(dsl).getOrNull()?.warnings.orEmpty()
        assertTrue(warnings.any { it.message.contains("Duplicate variable") })
    }

    private fun sampleDsl() = """
        {
          "dslVersion": 1,
          "metadata": { "name": "Water Tracker" },
          "data": { "variables": [] },
          "actions": [],
          "ui": { "type": "TEXT", "value": "Hello" }
        }
    """.trimIndent()
}
