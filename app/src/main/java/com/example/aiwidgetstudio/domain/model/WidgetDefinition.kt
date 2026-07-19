package com.example.aiwidgetstudio.domain.model

data class WidgetDefinition(
    val metadata: WidgetMetadata,
    val data: WidgetData,
    val actions: List<WidgetAction>,
    val ui: UiNode,
    val conditions: List<RenderCondition> = emptyList()
)

enum class WidgetSize(val label: String, val widthDp: Int, val heightDp: Int) {
    SMALL("2×1", 180, 80),
    MEDIUM("2×2", 180, 180),
    WIDE("4×2", 360, 180),
    LARGE("4×4", 360, 360)
}

data class WidgetMetadata(
    val name: String,
    val size: WidgetSize = WidgetSize.MEDIUM
)

data class WidgetData(
    val updatePolicy: UpdatePolicy,
    val variables: List<VariableDefinition>
)
