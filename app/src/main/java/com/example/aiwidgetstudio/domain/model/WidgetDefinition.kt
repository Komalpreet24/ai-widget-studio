package com.example.aiwidgetstudio.domain.model

data class WidgetDefinition(
    val metadata: WidgetMetadata,
    val data: WidgetData,
    val actions: List<WidgetAction>,
    val ui: UiNode
)

data class WidgetMetadata(
    val name: String
)

data class WidgetData(
    val updatePolicy: UpdatePolicy,
    val variables: List<VariableDefinition>
)
