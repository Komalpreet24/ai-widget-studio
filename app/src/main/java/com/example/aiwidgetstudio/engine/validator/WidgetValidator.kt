package com.example.aiwidgetstudio.engine.validator

import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetDefinition

class WidgetValidator {

    private val bindingRegex = Regex("""^\{\{([A-Za-z_][A-Za-z0-9_]*)}}$""")

    fun validate(widget: WidgetDefinition): List<WidgetValidatorWarning> {
        val warnings = mutableListOf<WidgetValidatorWarning>()

        val variablesByName = widget.data.variables.associateBy { it.name }
        val variableNames = variablesByName.keys
        val actionIds = widget.actions.map { it.id }

        variableNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            warnings += WidgetValidatorWarning("Duplicate variable name $it")
        }

        actionIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            warnings += WidgetValidatorWarning("Duplicate action id $it")
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

            if (target != null && target !in variableNames) {
                warnings += WidgetValidatorWarning("Action '${action.id}' targets missing variable: $target")
            }
        }

        validateUiNode(widget.ui, warnings, actionIds, variablesByName)

        return warnings
    }

    private fun validateUiNode(
        ui: UiNode,
        warnings: MutableList<WidgetValidatorWarning>,
        actionIds: List<String>,
        variableByNames: Map<String, VariableDefinition>
    ) {
        when (ui) {
            is UiNode.Box -> validateUiNode(ui.child, warnings, actionIds, variableByNames)
            is UiNode.Button -> if (ui.action.isBlank()) {
                warnings += WidgetValidatorWarning("Button missing action id")
            } else if (ui.action !in actionIds) {
                warnings += WidgetValidatorWarning("Action id ${ui.action} related to button missing definition")
            }

            is UiNode.Card -> validateUiNode(ui.child, warnings, actionIds, variableByNames)
            is UiNode.Column -> ui.children.forEach {
                validateUiNode(it, warnings, actionIds, variableByNames)
            }

            is UiNode.Icon -> if (ui.icon.isBlank()) {
                warnings += WidgetValidatorWarning("Icon missing for Icon node")
            }

            is UiNode.Progress -> validateProgressValue(
                ui, warnings, variableByNames
            )

            is UiNode.Row -> ui.children.forEach {
                validateUiNode(it, warnings, actionIds, variableByNames)
            }

            is UiNode.Text -> if (ui.value.isBlank()) {
                warnings += WidgetValidatorWarning("Value missing for Text Node")
            }

            else -> Unit
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
                            "Variable type should be either integer or double'"
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
}
