package com.example.aiwidgetstudio.domain.model

sealed interface VariableDefinition {
    val name: String
    data class IntegerVariable(override val name: String, val default: Int, val min: Int?, val max: Int?): VariableDefinition
    data class BooleanVariable(override val name: String, val default: Boolean): VariableDefinition
    data class StringVariable(override val name: String, val default: String): VariableDefinition
    data class DoubleVariable(override val name: String, val default: Double, val min: Double?, val max: Double?) : VariableDefinition
}
