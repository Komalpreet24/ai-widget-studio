package com.example.aiwidgetstudio.domain.model

sealed interface VariableDefinition {
    val name: String
    val source: DataSource?

    data class IntegerVariable(
        override val name: String,
        val default: Int,
        val min: Int?,
        val max: Int?,
        override val source: DataSource? = null
    ) : VariableDefinition

    data class BooleanVariable(
        override val name: String,
        val default: Boolean,
        override val source: DataSource? = null
    ) : VariableDefinition

    data class StringVariable(
        override val name: String,
        val default: String,
        override val source: DataSource? = null
    ) : VariableDefinition

    data class DoubleVariable(
        override val name: String,
        val default: Double,
        val min: Double?,
        val max: Double?,
        override val source: DataSource? = null
    ) : VariableDefinition
}
