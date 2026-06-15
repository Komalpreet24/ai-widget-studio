package com.example.aiwidgetstudio.engine.parser.mapper

import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UiNodeMargin
import com.example.aiwidgetstudio.domain.model.UiNodeStyle
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetAlignment
import com.example.aiwidgetstudio.domain.model.WidgetData
import com.example.aiwidgetstudio.domain.model.WidgetMetadata
import com.example.aiwidgetstudio.engine.parser.dto.DslDataDto
import com.example.aiwidgetstudio.engine.parser.dto.DslMetadataDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

class WidgetDslMapper {

    fun mapMetadata(metadata: DslMetadataDto?): WidgetMetadata {
        return WidgetMetadata(
            metadata?.name
                ?.takeIf { it.isNotBlank() }
                ?: "Untitled"
        )
    }

    fun mapData(data: DslDataDto?): WidgetData {
        return WidgetData(
            mapUpdatePolicy(data?.updatePolicy),
            data?.variables?.mapNotNull {
                mapVariable(it)
            } ?: emptyList()
        )
    }

    fun mapActions(actions: List<JsonElement>): List<WidgetAction> {
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

            "OPEN_APP" -> WidgetAction.OpenApp(
                id
            )

            else -> null
        }
    }

    fun mapUiNode(ui: JsonElement?): UiNode? { //TODO: break into smaller functions
        val obj = ui?.asObjectOrNull() ?: return null

        val styleObj = obj["style"]?.asObjectOrNull()
        val marginObj = styleObj?.get("margin")?.asObjectOrNull()
        val paddingObj = styleObj?.get("padding")?.asObjectOrNull()
        val textColor: String? = styleObj?.toStringOrNull("textColor")
        val backgroundColor: String? = styleObj?.toStringOrNull("backgroundColor")
        val cornerRadius: Int? = styleObj?.toIntOrNull("cornerRadius")
        val margin: UiNodeMargin? = UiNodeMargin(
            left = marginObj?.toIntOrNull("left"),
            right = marginObj?.toIntOrNull("right"),
            top = marginObj?.toIntOrNull("top"),
            bottom = marginObj?.toIntOrNull("bottom")
        )
        val padding: UiNodeMargin? = UiNodeMargin(
            left = paddingObj?.toIntOrNull("left"),
            right = paddingObj?.toIntOrNull("right"),
            top = paddingObj?.toIntOrNull("top"),
            bottom = paddingObj?.toIntOrNull("bottom")
        )
        val alignment: WidgetAlignment = when (styleObj?.toStringOrNull("alignment")?.uppercase()) {
            "START" -> WidgetAlignment.START
            "CENTER" -> WidgetAlignment.CENTER
            "END" -> WidgetAlignment.END
            else -> WidgetAlignment.CENTER
        }
        val arrangement: WidgetAlignment? =
            when (styleObj?.toStringOrNull("arrangement")?.uppercase()) {
                "START" -> WidgetAlignment.START
                "CENTER" -> WidgetAlignment.CENTER
                "END" -> WidgetAlignment.END
                else -> WidgetAlignment.CENTER
            }
        val height: Int? = styleObj?.toIntOrNull("height")
        val width: Int? = styleObj?.toIntOrNull("width")

        val style = UiNodeStyle(
            textColor = textColor,
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            margin = margin,
            padding = padding,
            alignment = alignment,
            arrangement = arrangement,
            height = height,
            width = width,
        )

        return when (obj.toStringOrNull("type")?.uppercase()) {
            "CARD" -> {
                UiNode.Card(
                    style = style,
                    child = mapUiNode(obj["child"]) ?: return null
                )
            }

            "COL", "COLUMN" -> {
                UiNode.Column(
                    style = style,
                    children = obj.asJsonArray("children")?.mapNotNull {
                        mapUiNode(it)
                    } ?: return null
                )
            }

            "ROW" -> {
                UiNode.Row(
                    style = style,
                    children = obj.asJsonArray("children")?.mapNotNull {
                        mapUiNode(it)
                    } ?: return null
                )
            }

            "BOX" -> {
                UiNode.Box(
                    style = style,
                    child = mapUiNode(obj["child"]) ?: return null
                )
            }

            "BUTTON" -> {
                UiNode.Button(
                    style = style,
                    text = obj.toStringOrNull("text") ?: "",
                    action = obj.toStringOrNull("action") ?: ""
                )
            }

            "TEXT" -> {
                UiNode.Text(
                    style = style,
                    value = obj.toStringOrNull("value") ?: ""
                )
            }

            "SPACER" -> {
                UiNode.Spacer(
                    style = style,
                    width = obj.toIntOrNull("width"),
                    height = obj.toIntOrNull("height")
                )
            }

            "PROGRESS" -> {
                UiNode.Progress(
                    style = style,
                    current = obj.toStringOrNull("current") ?: return null,
                    max = obj.toStringOrNull("max") ?: return null
                )
            }

            "DIVIDER" -> {
                UiNode.Divider(
                    style = style
                )
            }

            "ICON" -> {
                UiNode.Icon(
                    style = style,
                    icon = obj.toStringOrNull("icon") ?: return null
                )
            }

            else -> null
        }
    }

    //JSON Helper Functions
    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return this as? JsonObject
    }

    private fun JsonObject.asJsonArray(key: String): JsonArray? {
        return (this[key] as? JsonPrimitive)?.jsonArray
    }

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
