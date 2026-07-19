package com.example.aiwidgetstudio.engine

import com.example.aiwidgetstudio.domain.model.ConditionOperator
import com.example.aiwidgetstudio.domain.model.ConditionalStyle
import com.example.aiwidgetstudio.domain.model.RenderCondition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.engine.state.WidgetState
import javax.inject.Inject

class ConditionEvaluator @Inject constructor() {

    /** Returns merged style override from all matching conditions, or null if none match. */
    fun evaluate(conditions: List<RenderCondition>, state: WidgetState): ConditionalStyle? {
        val matching = conditions.filter { matches(it, state) }
        if (matching.isEmpty()) return null
        return matching.fold(ConditionalStyle()) { acc, cond ->
            ConditionalStyle(
                backgroundColor = cond.styleOverride.backgroundColor ?: acc.backgroundColor,
                textColor = cond.styleOverride.textColor ?: acc.textColor
            )
        }
    }

    private fun matches(condition: RenderCondition, state: WidgetState): Boolean {
        val raw = state.values[condition.variable] ?: return false
        val actual = raw.toDouble() ?: return false
        return when (condition.operator) {
            ConditionOperator.GT -> actual > condition.value
            ConditionOperator.GTE -> actual >= condition.value
            ConditionOperator.LT -> actual < condition.value
            ConditionOperator.LTE -> actual <= condition.value
            ConditionOperator.EQ -> actual == condition.value
        }
    }

    private fun VariableValue.toDouble(): Double? = when (this) {
        is VariableValue.IntValue -> value.toDouble()
        is VariableValue.DoubleValue -> value
        is VariableValue.BooleanValue -> if (value) 1.0 else 0.0
        is VariableValue.StringValue -> value.toDoubleOrNull()
    }
}
