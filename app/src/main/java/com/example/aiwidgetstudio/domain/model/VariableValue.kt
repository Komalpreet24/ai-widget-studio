package com.example.aiwidgetstudio.domain.model

sealed interface VariableValue {
    data class IntValue(val value: Int) : VariableValue
    data class StringValue(val value: String) : VariableValue
    data class BooleanValue(val value: Boolean) : VariableValue
    data class DoubleValue(val value: Double) : VariableValue
}
