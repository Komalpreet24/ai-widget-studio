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

internal fun Int.clamp(min: Int?, max: Int?): Int = when {
    min != null && max != null -> if (min <= max) coerceIn(min, max) else this
    min != null -> coerceAtLeast(min)
    max != null -> coerceAtMost(max)
    else -> this
}

internal fun Long.clamp(min: Long, max: Long): Long =
    if (min <= max) coerceIn(min, max) else this

internal fun Double.clamp(min: Double?, max: Double?): Double = when {
    min != null && max != null -> if (min <= max) coerceIn(min, max) else this
    min != null -> coerceAtLeast(min)
    max != null -> coerceAtMost(max)
    else -> this
}
