package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetDefinition

class WidgetStateEngine {

    fun createInitialState(definition: WidgetDefinition): WidgetState = WidgetState(
        values = definition.data.variables.associate { variable ->
            variable.name to variable.defaultValue()
        }
    )

    fun applyAction(
        definition: WidgetDefinition,
        currentState: WidgetState,
        actionId: String
    ): WidgetState {
        val action = definition.actions.find { it.id == actionId } ?: return currentState

        val target = when (action) {
            is WidgetAction.Increment -> action.target
            is WidgetAction.Decrement -> action.target
            is WidgetAction.Reset -> action.target
            is WidgetAction.SetValue -> action.target
            is WidgetAction.Toggle -> action.target
            is WidgetAction.OpenApp, is WidgetAction.OpenUrl -> return currentState
        }

        val variable = definition.data.variables.find { it.name == target } ?: return currentState
        val currentValue = currentState.values[target] ?: return currentState

        val newValue = when (action) {
            is WidgetAction.Increment -> updateNumber(currentValue, variable, action.step.toLong())
            is WidgetAction.Decrement -> updateNumber(currentValue, variable, -action.step.toLong())
            is WidgetAction.Reset -> constrain(variable.defaultValue(), variable)
            is WidgetAction.SetValue -> constrain(action.value, variable)
            is WidgetAction.Toggle -> if (
                currentValue is VariableValue.BooleanValue &&
                variable is VariableDefinition.BooleanVariable
            ) {
                VariableValue.BooleanValue(!currentValue.value)
            } else null

            is WidgetAction.OpenApp, is WidgetAction.OpenUrl -> null
        } ?: return currentState

        return currentState.copy(values = currentState.values + (target to newValue))
    }

    private fun updateNumber(
        currentValue: VariableValue,
        variable: VariableDefinition,
        delta: Long
    ): VariableValue? = when {
        currentValue is VariableValue.IntValue && variable is VariableDefinition.IntegerVariable -> {
            val min = variable.min ?: Int.MIN_VALUE
            val max = variable.max ?: Int.MAX_VALUE
            if (min > max) null else VariableValue.IntValue(
                (currentValue.value.toLong() + delta)
                    .clamp(min.toLong(), max.toLong())
                    .toInt()
            )
        }

        currentValue is VariableValue.DoubleValue && variable is VariableDefinition.DoubleVariable -> {
            val min = variable.min ?: -Double.MAX_VALUE
            val max = variable.max ?: Double.MAX_VALUE
            if (min > max) null else VariableValue.DoubleValue(
                (currentValue.value + delta).clamp(min, max)
            )
        }

        else -> null
    }

    private fun constrain(
        value: VariableValue,
        variable: VariableDefinition
    ): VariableValue? = when {
        value is VariableValue.IntValue && variable is VariableDefinition.IntegerVariable -> {
            val min = variable.min ?: Int.MIN_VALUE
            val max = variable.max ?: Int.MAX_VALUE
            if (min > max) null else VariableValue.IntValue(value.value.clamp(min, max))
        }

        value is VariableValue.DoubleValue && variable is VariableDefinition.DoubleVariable -> {
            val min = variable.min ?: -Double.MAX_VALUE
            val max = variable.max ?: Double.MAX_VALUE
            if (min > max) null else VariableValue.DoubleValue(value.value.clamp(min, max))
        }

        value is VariableValue.BooleanValue && variable is VariableDefinition.BooleanVariable -> value
        value is VariableValue.StringValue && variable is VariableDefinition.StringVariable -> value
        else -> null
    }
}
