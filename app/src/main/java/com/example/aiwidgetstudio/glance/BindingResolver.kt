package com.example.aiwidgetstudio.glance

import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.engine.state.WidgetState

object BindingResolver {

    private val bindingRegex = Regex("""^\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}$""")

    fun resolveText(value: String, state: WidgetState): String {
        val binding = extractVariableName(value) ?: return value
        return when (val resolved = state.values[binding]) {
            is VariableValue.BooleanValue -> resolved.value.toString()
            is VariableValue.DoubleValue -> resolved.value.toString()
            is VariableValue.IntValue -> resolved.value.toString()
            is VariableValue.StringValue -> resolved.value
            null -> ""
        }
    }

    fun resolveNumber(value: String, state: WidgetState): Double {
        extractVariableName(value)?.let { binding ->
            return when (val resolved = state.values[binding]) {
                is VariableValue.DoubleValue -> resolved.value
                is VariableValue.IntValue -> resolved.value.toDouble()
                else -> 0.0
            }
        }
        return value.toDoubleOrNull() ?: 0.0
    }

    fun progressRatio(current: String, max: String, state: WidgetState): Float {
        val maxValue = resolveNumber(max, state)
        if (maxValue <= 0.0) return 0f
        val currentValue = resolveNumber(current, state)
        return (currentValue / maxValue).toFloat().coerceIn(0f, 1f)
    }

    private fun extractVariableName(value: String): String? {
        return bindingRegex.find(value)?.groupValues?.get(1)
    }
}
