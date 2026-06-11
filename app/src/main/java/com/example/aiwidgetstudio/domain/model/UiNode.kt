package com.example.aiwidgetstudio.domain.model

sealed interface UiNode {
    val style: UiNodeStyle

    data class Card(override val style: UiNodeStyle, val child: UiNode): UiNode
    data class Column(override val style: UiNodeStyle, val children: List<UiNode>): UiNode
    data class Row(override val style: UiNodeStyle, val children: List<UiNode>): UiNode
    data class Box(override val style: UiNodeStyle, val child: UiNode): UiNode
    data class Button(override val style: UiNodeStyle, val text: String, val action: String): UiNode
    data class Text(override val style: UiNodeStyle, val value: String): UiNode
    data class Spacer(override val style: UiNodeStyle, val width: Int? = null, val height: Int? = null): UiNode
    data class Progress(override val style: UiNodeStyle, val current: String, val max: String): UiNode
    data class Divider(override val style: UiNodeStyle): UiNode
    data class Icon(override val style: UiNodeStyle, val icon: String): UiNode
}

data class UiNodeStyle (
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val cornerRadius: Int? = null,
    val margin: UiNodeMargin? = null,
    val padding: UiNodeMargin? = null,
    val alignment: WidgetAlignment? = null,
    val arrangement: WidgetAlignment? = null,
    val height: Int? = null,
    val width: Int? = null
)

data class UiNodeMargin(
    val left: Int? = null,
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null
)

enum class WidgetAlignment {
    START,
    CENTER,
    END
}