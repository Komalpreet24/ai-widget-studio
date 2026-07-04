package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

class WidgetStateCodec @Inject constructor() {

    fun encode(state: WidgetState): String = buildJsonObject {
        state.values.forEach { (name, value) ->
            put(name, value.toJsonPrimitive())
        }
    }.toString()

    fun decode(stateJson: String, definition: WidgetDefinition): WidgetState {
        val jsonObject = runCatching {
            Json.parseToJsonElement(stateJson) as? JsonObject
        }.getOrNull()

        return WidgetState(
            values = definition.data.variables.associate { variable ->
                variable.name to ((jsonObject?.get(variable.name) as? JsonPrimitive)?.let {
                    decodeValue(it, variable)
                } ?: variable.defaultValue())
            }
        )
    }

    private fun VariableValue.toJsonPrimitive(): JsonPrimitive = when (this) {
        is VariableValue.BooleanValue -> JsonPrimitive(value)
        is VariableValue.DoubleValue -> JsonPrimitive(value)
        is VariableValue.IntValue -> JsonPrimitive(value)
        is VariableValue.StringValue -> JsonPrimitive(value)
    }

    private fun decodeValue(
        value: JsonPrimitive,
        variable: VariableDefinition
    ): VariableValue = when (variable) {
        is VariableDefinition.BooleanVariable -> value.booleanOrNull
            ?.let(VariableValue::BooleanValue)
            ?: variable.defaultValue()

        is VariableDefinition.DoubleVariable -> value.doubleOrNull
            ?.let { VariableValue.DoubleValue(it.clamp(variable.min, variable.max)) }
            ?: variable.defaultValue()

        is VariableDefinition.IntegerVariable -> value.intOrNull
            ?.let { VariableValue.IntValue(it.clamp(variable.min, variable.max)) }
            ?: variable.defaultValue()

        is VariableDefinition.StringVariable -> value.contentOrNull
            ?.takeIf { value.isString }
            ?.let(VariableValue::StringValue)
            ?: variable.defaultValue()
    }
}
