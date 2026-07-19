package com.example.aiwidgetstudio.domain.model

/**
 * A conditional style override evaluated at render time.
 * e.g. if instagram_time > 60 → override backgroundColor to #FF0000
 */
data class RenderCondition(
    val variable: String,
    val operator: ConditionOperator,
    val value: Double,
    val styleOverride: ConditionalStyle
)

enum class ConditionOperator { GT, GTE, LT, LTE, EQ }

data class ConditionalStyle(
    val backgroundColor: String? = null,
    val textColor: String? = null
)
