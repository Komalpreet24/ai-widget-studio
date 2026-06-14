package com.example.aiwidgetstudio.engine.parser.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DslWidgetDto(
    val dslVersion: Int? = null,
    val data: DslDataDto? = null,
    val metadata: DslMetadataDto? = null,
    val actions: List<JsonElement> = emptyList(),
    val ui: JsonElement
)

@Serializable
data class DslMetadataDto(
    val name: String? = null
)

@Serializable
data class DslDataDto(
    val updatePolicy: JsonElement,
    val variables: List<JsonElement>
)
