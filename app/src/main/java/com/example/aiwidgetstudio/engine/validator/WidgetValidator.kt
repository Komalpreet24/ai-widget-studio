package com.example.aiwidgetstudio.engine.validator

import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import javax.inject.Inject

class WidgetValidator @Inject constructor() {

    private val bindingRegex = Regex("""^\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}$""")

    fun validate(widget: WidgetDefinition): List<WidgetValidatorWarning> {
        val warnings = mutableListOf<WidgetValidatorWarning>()

        val variablesByName = widget.data.variables.associateBy { it.name }
        val variableNames = widget.data.variables.map { it.name }
        val actionIds = widget.actions.map { it.id }

        variableNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            warnings += WidgetValidatorWarning("Duplicate variable name $it. Variable names must be unique.")
        }

        actionIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            warnings += WidgetValidatorWarning("Duplicate action id $it. Action ids must be unique.")
        }

        widget.data.variables.forEach { variable ->
            when (variable) {
                is VariableDefinition.DoubleVariable -> validateNumericVariable(
                    variable.name,
                    variable.default,
                    variable.min,
                    variable.max,
                    warnings
                )

                is VariableDefinition.IntegerVariable -> validateNumericVariable(
                    variable.name,
                    variable.default.toDouble(),
                    variable.min?.toDouble(),
                    variable.max?.toDouble(),
                    warnings
                )

                else -> Unit
            }
        }

        widget.actions.forEach { action ->
            val target = when (action) {
                is WidgetAction.Decrement -> action.target
                is WidgetAction.Increment -> action.target
                is WidgetAction.Reset -> action.target
                is WidgetAction.SetValue -> action.target
                is WidgetAction.Toggle -> action.target
                is WidgetAction.OpenApp -> null
                is WidgetAction.OpenUrl -> null
            }

            val variable = target?.let(variablesByName::get)

            if (target != null && variable == null) {
                warnings += WidgetValidatorWarning("Action '${action.id}' references unknown variable: $target")
                return@forEach
            }

            when (action) {
                is WidgetAction.Decrement -> if (variable !is VariableDefinition.IntegerVariable && variable !is VariableDefinition.DoubleVariable) {
                    warnings += WidgetValidatorWarning(
                        "Action ${action.id} cannot decrement variable $target. Only INT and DOUBLE variables support DECREMENT."
                    )
                }

                is WidgetAction.Increment -> if (variable !is VariableDefinition.IntegerVariable && variable !is VariableDefinition.DoubleVariable) {
                    warnings += WidgetValidatorWarning(
                        "Action ${action.id} cannot increment variable $target. Only INT and DOUBLE variables support INCREMENT."
                    )
                }

                is WidgetAction.OpenUrl -> if (action.url.isBlank()) {
                    warnings += WidgetValidatorWarning("Invalid URL for type $action")
                }

                is WidgetAction.Reset -> Unit

                is WidgetAction.SetValue -> when (variable) {
                    is VariableDefinition.BooleanVariable -> if (action.value !is VariableValue.BooleanValue) warnings += WidgetValidatorWarning(
                        "Action ${action.value} assigns a ${action.value::class.simpleName} value to BOOLEAN variable $target."
                    )

                    is VariableDefinition.DoubleVariable -> if (action.value !is VariableValue.DoubleValue) warnings += WidgetValidatorWarning(
                        "Action ${action.value} assigns a ${action.value::class.simpleName} value to DOUBLE variable $target."
                    )

                    is VariableDefinition.IntegerVariable -> if (action.value !is VariableValue.IntValue) warnings += WidgetValidatorWarning(
                        "Action ${action.value} assigns a ${action.value::class.simpleName} value to INT variable $target."
                    )

                    is VariableDefinition.StringVariable -> if (action.value !is VariableValue.StringValue) warnings += WidgetValidatorWarning(
                        "Action ${action.value} assigns a ${action.value::class.simpleName} value to STRING variable $target."
                    )

                    null -> Unit
                }

                is WidgetAction.Toggle -> if (variable !is VariableDefinition.BooleanVariable) {
                    warnings += WidgetValidatorWarning(
                        "Action ${action.id} can only target BOOLEAN variables. Variable $target is not BOOLEAN."
                    )
                }

                else -> Unit
            }

        }

        when (widget.data.updatePolicy) {
            is UpdatePolicy.DailyReset -> when {
                (widget.data.updatePolicy.minute !in 0..59) -> warnings += WidgetValidatorWarning(
                    "Invalid minute value"
                )

                (widget.data.updatePolicy.hour !in 0..23) -> warnings += WidgetValidatorWarning(
                    "Invalid hour value"
                )
            }

            is UpdatePolicy.Periodic -> if (widget.data.updatePolicy.intervalMinutes <= 0) warnings += WidgetValidatorWarning(
                "Invalid interval minutes"
            )

            UpdatePolicy.None -> Unit
        }

        validateUiNode(widget.ui, warnings, actionIds, variablesByName)

        return warnings
    }

    private fun validateNumericVariable(
        name: String,
        default: Double,
        min: Double?,
        max: Double?,
        warnings: MutableList<WidgetValidatorWarning>
    ) {
        if (min != null && max != null && min > max) {
            warnings += WidgetValidatorWarning("Variable '$name' has min greater than max")
        }

        if ((min != null && default < min) || (max != null && default > max)) {
            warnings += WidgetValidatorWarning("Variable '$name' has default value outside its range")
        }
    }

    private fun validateUiNode(
        ui: UiNode,
        warnings: MutableList<WidgetValidatorWarning>,
        actionIds: List<String>,
        variableByNames: Map<String, VariableDefinition>,
        depth: Int = 1,
        nodesVisited: Int = 0
    ): Int {
        if (nodesVisited >= MAX_UI_NODES) {
            if (nodesVisited == MAX_UI_NODES) {
                warnings += WidgetValidatorWarning("UI exceeds maximum node count of $MAX_UI_NODES")
            }
            return MAX_UI_NODES + 1
        }

        val updatedNodeCount = nodesVisited + 1
        if (depth > MAX_UI_DEPTH) {
            warnings += WidgetValidatorWarning("UI exceeds maximum depth of $MAX_UI_DEPTH")
            return updatedNodeCount
        }

        when (ui) {
            is UiNode.Box -> return validateUiNode(
                ui.child, warnings, actionIds, variableByNames, depth + 1, updatedNodeCount
            )

            is UiNode.Button -> if (ui.action.isBlank()) {
                warnings += WidgetValidatorWarning("Button missing action id")
            } else if (ui.action !in actionIds) {
                warnings += WidgetValidatorWarning("Action id ${ui.action} related to button missing definition")
            }

            is UiNode.Card -> return validateUiNode(
                ui.child, warnings, actionIds, variableByNames, depth + 1, updatedNodeCount
            )

            is UiNode.Column -> {
                warnIfTooManyChildren(ui.children.size, warnings)
                return ui.children.take(MAX_UI_CHILDREN).fold(updatedNodeCount) { count, child ->
                    validateUiNode(child, warnings, actionIds, variableByNames, depth + 1, count)
                }
            }

            is UiNode.Icon -> if (ui.icon.isBlank()) {
                warnings += WidgetValidatorWarning("Icon missing for Icon node")
            }

            is UiNode.Progress -> validateProgressValue(
                ui, warnings, variableByNames
            )

            is UiNode.Row -> {
                warnIfTooManyChildren(ui.children.size, warnings)
                return ui.children.take(MAX_UI_CHILDREN).fold(updatedNodeCount) { count, child ->
                    validateUiNode(child, warnings, actionIds, variableByNames, depth + 1, count)
                }
            }

            is UiNode.Text -> if (ui.value.isBlank()) {
                warnings += WidgetValidatorWarning("Value missing for Text Node")
            }

            else -> Unit
        }

        return updatedNodeCount
    }

    private fun warnIfTooManyChildren(
        childCount: Int,
        warnings: MutableList<WidgetValidatorWarning>
    ) {
        if (childCount > MAX_UI_CHILDREN) {
            warnings += WidgetValidatorWarning(
                "Layout has $childCount children; only the first $MAX_UI_CHILDREN will render"
            )
        }
    }

    private fun validateProgressValue(
        ui: UiNode.Progress,
        warnings: MutableList<WidgetValidatorWarning>,
        variableByNames: Map<String, VariableDefinition>
    ) {

        validateProgressField("current", ui.current, variableByNames, warnings)
        validateProgressField("max", ui.max, variableByNames, warnings)

        val current = ui.current.toDoubleOrNull()
        val max = ui.max.toDoubleOrNull()

        if (current != null && max != null && current > max) {
            warnings += WidgetValidatorWarning(
                "Progress current cannot be greater than max"
            )
        }

    }

    private fun validateProgressField(
        field: String,
        value: String,
        variableByNames: Map<String, VariableDefinition>,
        warnings: MutableList<WidgetValidatorWarning>
    ) {
        val binding = extractVariableName(value)
        when {
            binding != null -> {
                when (variableByNames[binding]) {
                    null -> warnings += WidgetValidatorWarning(
                        "Progress $field references missing variable '$binding'"
                    )

                    is VariableDefinition.IntegerVariable, is VariableDefinition.DoubleVariable -> Unit
                    else -> {
                        warnings += WidgetValidatorWarning(
                            "Variable type should be either integer or double"
                        )
                    }
                }
            }

            value.toDoubleOrNull() == null ->
                warnings += WidgetValidatorWarning(
                    "Progress $field must be a number or variable binding"
                )
        }
    }

    private fun extractVariableName(value: String): String? {
        return bindingRegex.find(value)?.groupValues[1]
    }

    private companion object {
        const val MAX_UI_DEPTH = 10
        const val MAX_UI_NODES = 100
        const val MAX_UI_CHILDREN = 20
    }
}
