package com.example.aiwidgetstudio.engine.parser

import com.example.aiwidgetstudio.engine.parser.dto.DslWidgetDto
import kotlinx.serialization.json.Json

class WidgetDslParser {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): Result<DslWidgetDto> {
        return runCatching {
            json.decodeFromString<DslWidgetDto>(rawJson)
        }
    }
}
