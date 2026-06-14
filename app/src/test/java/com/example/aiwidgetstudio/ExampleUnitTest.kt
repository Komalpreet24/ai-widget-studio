package com.example.aiwidgetstudio

import com.example.aiwidgetstudio.engine.parser.WidgetDslParser
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
}