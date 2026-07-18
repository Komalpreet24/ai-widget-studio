package com.example.aiwidgetstudio.engine.parser.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DslWidgetDto(
    val dslVersion: Int? = null,
    val metadata: DslMetadataDto? = null,
    val data: DslDataDto? = null,
    val actions: List<JsonElement> = emptyList(),
    val ui: JsonElement? = null
)

@Serializable
data class DslMetadataDto(
    val name: String? = null,
    val size: String? = null
)

@Serializable
data class DslDataDto(
    val updatePolicy: JsonElement? = null,
    val variables: List<JsonElement> = emptyList()
)
