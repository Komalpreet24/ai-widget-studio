package com.example.aiwidgetstudio.engine.parser.mapper

import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UiNodeMargin
import com.example.aiwidgetstudio.domain.model.UiNodeStyle
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetAlignment
import com.example.aiwidgetstudio.domain.model.WidgetData
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.domain.model.WidgetMetadata
import com.example.aiwidgetstudio.engine.parser.WidgetDslParser
import com.example.aiwidgetstudio.engine.parser.dto.DslDataDto
import com.example.aiwidgetstudio.engine.parser.dto.DslMetadataDto
import com.example.aiwidgetstudio.engine.parser.dto.DslWidgetDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class WidgetDslMapper {

    fun parseWidget(rawJson: String): Result<WidgetDefinition> {
        return WidgetDslParser().parse(rawJson).map { map(it) }
    }

    private fun map(widget: DslWidgetDto) = WidgetDefinition(
        mapMetadata(widget.metadata),
        mapData(widget.data),
        mapActions(widget.actions),
        mapUiNode(widget.ui)
    )

    private fun mapMetadata(metadata: DslMetadataDto?): WidgetMetadata {
        return WidgetMetadata(
            metadata?.name
                ?.takeIf { it.isNotBlank() }
                ?: "Untitled"
        )
    }

    private fun mapData(data: DslDataDto?): WidgetData {
        return WidgetData(
            mapUpdatePolicy(data?.updatePolicy),
            data?.variables?.mapNotNull {
                mapVariable(it)
            } ?: emptyList()
        )
    }

    private fun mapActions(actions: List<JsonElement>): List<WidgetAction> {
        return actions.mapNotNull {
            mapAction(it)
        }
    }

    private fun mapUpdatePolicy(updatePolicy: JsonElement?): UpdatePolicy {
        val obj = updatePolicy?.asObjectOrNull() ?: return UpdatePolicy.None

        return when (obj.toStringOrNull("type")?.uppercase()) {
            "DAILY_RESET" -> UpdatePolicy.DailyReset(
                obj.toIntOrNull("hour") ?: 0,
                obj.toIntOrNull("minute") ?: 0
            )

            "PERIODIC", "PERIODIC_RESET" -> UpdatePolicy.Periodic(
                obj.toLongOrNull("intervalMinutes") ?: 60L
            )

            "NONE", null -> UpdatePolicy.None
            else -> UpdatePolicy.None
        }
    }

    private fun mapVariable(variable: JsonElement?): VariableDefinition? {
        val obj = variable?.asObjectOrNull() ?: return null

        val name = obj.toStringOrNull("name")?.takeIf { it.isNotBlank() } ?: return null

        return when (obj.toStringOrNull("type")?.uppercase()) {
            "INT", "INTEGER" -> VariableDefinition.IntegerVariable(
                name,
                obj.toIntOrNull("default") ?: 0,
                obj.toIntOrNull("min"),
                obj.toIntOrNull("max")
            )

            "BOOL", "BOOLEAN" -> VariableDefinition.BooleanVariable(
                name,
                obj.toBooleanOrNull("default") ?: false
            )

            "STR", "STRING" -> VariableDefinition.StringVariable(
                name,
                obj.toStringOrNull("default") ?: ""
            )

            "DOUBLE" -> VariableDefinition.DoubleVariable(
                name,
                obj.toDoubleOrNull("default") ?: 0.0,
                obj.toDoubleOrNull("min"),
                obj.toDoubleOrNull("max")
            )

            else -> VariableDefinition.StringVariable(
                name,
                obj.toStringOrNull("default") ?: ""
            )
        }
    }

    private fun mapAction(action: JsonElement?): WidgetAction? {
        val obj = action?.asObjectOrNull() ?: return null

        val id = obj.toStringOrNull("id")?.takeIf { it.isNotBlank() } ?: return null

        return when (obj.toStringOrNull("type")?.uppercase()) {
            "INCREMENT", "ADD" -> WidgetAction.Increment(
                id,
                obj.toIntOrNull("step") ?: 1,
                obj.toStringOrNull("target")?.takeIf { it.isNotBlank() } ?: return null
            )

            "DECREMENT", "MINUS", "SUBTRACT" -> WidgetAction.Decrement(
                id,
                obj.toIntOrNull("step") ?: 1,
                obj.toStringOrNull("target")?.takeIf { it.isNotBlank() } ?: return null
            )

            "RESET" -> WidgetAction.Reset(
                id,
                obj.toStringOrNull("target")?.takeIf { it.isNotBlank() } ?: return null
            )

            "SET_VALUE" -> WidgetAction.SetValue(
                id = id,
                target = obj.toStringOrNull("target")?.takeIf { it.isNotBlank() } ?: return null,
                value = mapVariableValue(obj["value"]) ?: return null
            )

            "TOGGLE" -> WidgetAction.Toggle(
                id = id,
                target = obj.toStringOrNull("target")?.takeIf { it.isNotBlank() } ?: return null
            )

            "OPEN_APP" -> WidgetAction.OpenApp(
                id
            )

            "OPEN_URL" -> WidgetAction.OpenUrl(
                id = id,
                url = obj.toStringOrNull("url")?.takeIf { it.isNotBlank() } ?: return null
            )

            else -> null
        }
    }

    fun mapUiNode(ui: JsonElement?): UiNode {
        val obj = ui?.asObjectOrNull() ?: return fallbackUiNode()
        val style = mapStyle(obj["style"])
        val type = obj.toStringOrNull("type")?.uppercase()

        return mapLayoutNode(type, obj, style)
            ?: mapContentNode(type, obj, style)
            ?: fallbackUiNode()
    }

    private fun mapLayoutNode(type: String?, obj: JsonObject, style: UiNodeStyle): UiNode? =
        when (type) {
            "CARD" -> UiNode.Card(style, mapUiNode(obj["child"]))
            "BOX" -> UiNode.Box(style, mapUiNode(obj["child"]))
            "COL", "COLUMN" -> UiNode.Column(style, obj.arrayOrEmpty("children").map(::mapUiNode))
            "ROW" -> UiNode.Row(style, obj.arrayOrEmpty("children").map(::mapUiNode))
            else -> null
        }

    private fun mapContentNode(type: String?, obj: JsonObject, style: UiNodeStyle): UiNode? =
        when (type) {
            "BUTTON" -> UiNode.Button(
                style,
                obj.toStringOrNull("text") ?: "",
                obj.toStringOrNull("action") ?: ""
            )

            "TEXT" -> UiNode.Text(style, obj.toStringOrNull("value") ?: "")
            "SPACER" -> UiNode.Spacer(style, obj.toIntOrNull("width"), obj.toIntOrNull("height"))
            "PROGRESS" -> UiNode.Progress(
                style,
                obj.toStringOrNull("current") ?: "0",
                obj.toStringOrNull("max") ?: "1"
            )

            "DIVIDER" -> UiNode.Divider(style)
            "ICON" -> UiNode.Icon(style, obj.toStringOrNull("icon") ?: "?")
            else -> null
        }

    private fun mapStyle(element: JsonElement?): UiNodeStyle {
        val obj = element?.asObjectOrNull() ?: return UiNodeStyle()
        return UiNodeStyle(
            textColor = obj.toStringOrNull("textColor"),
            backgroundColor = obj.toStringOrNull("backgroundColor"),
            cornerRadius = obj.toIntOrNull("cornerRadius"),
            margin = mapMargin(obj["margin"]),
            padding = mapMargin(obj["padding"]),
            alignment = mapAlignment(obj.toStringOrNull("alignment")),
            arrangement = mapAlignment(obj.toStringOrNull("arrangement")),
            height = obj.toIntOrNull("height"),
            width = obj.toIntOrNull("width")
        )
    }

    private fun mapMargin(element: JsonElement?): UiNodeMargin? {
        (element as? JsonPrimitive)?.intOrNull?.let { value ->
            return UiNodeMargin(value, value, value, value)
        }

        val obj = element?.asObjectOrNull() ?: return null
        return UiNodeMargin(
            left = obj.toIntOrNull("left"),
            top = obj.toIntOrNull("top"),
            right = obj.toIntOrNull("right"),
            bottom = obj.toIntOrNull("bottom")
        )
    }

    private fun mapAlignment(value: String?): WidgetAlignment? = when (value?.uppercase()) {
        "START", "LEFT" -> WidgetAlignment.START
        "CENTER", "CENTRE" -> WidgetAlignment.CENTER
        "END", "RIGHT" -> WidgetAlignment.END
        else -> null
    }

    private fun mapVariableValue(element: JsonElement?): VariableValue? {
        val primitive = element as? JsonPrimitive ?: return null
        return when {
            primitive.isString -> VariableValue.StringValue(primitive.content)
            primitive.booleanOrNull != null -> VariableValue.BooleanValue(primitive.booleanOrNull!!)
            primitive.intOrNull != null -> VariableValue.IntValue(primitive.intOrNull!!)
            primitive.doubleOrNull != null -> VariableValue.DoubleValue(primitive.doubleOrNull!!)
            else -> null
        }
    }

    private fun fallbackUiNode(): UiNode = UiNode.Text(UiNodeStyle(), "Unable to render widget")

    //JSON Helper Functions
    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return this as? JsonObject
    }

    private fun JsonObject.arrayOrEmpty(key: String): List<JsonElement> =
        (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.toStringOrNull(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.toIntOrNull(key: String): Int? {
        return (this[key] as? JsonPrimitive)?.intOrNull
    }

    private fun JsonObject.toLongOrNull(key: String): Long? {
        return (this[key] as? JsonPrimitive)?.longOrNull
    }

    private fun JsonObject.toBooleanOrNull(key: String): Boolean? {
        return (this[key] as? JsonPrimitive)?.booleanOrNull
    }

    private fun JsonObject.toDoubleOrNull(key: String): Double? {
        return (this[key] as? JsonPrimitive)?.doubleOrNull
    }
}
