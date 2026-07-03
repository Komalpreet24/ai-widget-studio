package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue

data class WidgetState(
    val values: Map<String, VariableValue>
)

internal fun VariableDefinition.defaultValue(): VariableValue = when (this) {
    is VariableDefinition.BooleanVariable -> VariableValue.BooleanValue(default)
    is VariableDefinition.DoubleVariable -> VariableValue.DoubleValue(default)
    is VariableDefinition.IntegerVariable -> VariableValue.IntValue(default)
    is VariableDefinition.StringVariable -> VariableValue.StringValue(default)
}
