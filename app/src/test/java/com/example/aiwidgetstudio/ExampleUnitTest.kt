package com.example.aiwidgetstudio

import com.example.aiwidgetstudio.engine.parser.WidgetDslParser
import com.example.aiwidgetstudio.engine.parser.dto.DslMetadataDto
import com.example.aiwidgetstudio.engine.parser.mapper.WidgetDslMapper
import kotlinx.serialization.json.Json
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun parser_readsRootDsl() {
        val rawJson = """
            {
              "dslVersion": 1,
              "metadata": {
                "name": "Water Tracker"
              },
              "data": {
                "updatePolicy": {
                  "type": "DAILY_RESET",
                  "hour": 0,
                  "minute": 0
                },
                "variables": []
              },
              "actions": [],
              "ui": {
                "type": "TEXT",
                "value": "Hello"
              }
            }
        """.trimIndent()

        val result = WidgetDslParser().parse(rawJson)

        assertTrue(result.isSuccess)
    }

    @Test
    fun mapper_keepsValidUiChildrenAndRepairsUnknownNodes() {
        val ui = Json.parseToJsonElement(
            """{"type":"COLUMN","children":[{"type":"TEXT","value":"Water"},{"type":"UNKNOWN"}]}"""
        )

        val node = WidgetDslMapper().mapUiNode(ui)

        assertEquals(2, (node as com.example.aiwidgetstudio.domain.model.UiNode.Column).children.size)
    }

}
