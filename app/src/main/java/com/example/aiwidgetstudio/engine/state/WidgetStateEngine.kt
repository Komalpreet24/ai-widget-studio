package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.WidgetDefinition

class WidgetStateEngine {
    fun createInitialState(definition: WidgetDefinition): WidgetState = WidgetState(
        values = definition.data.variables.associate { variable ->
            variable.name to variable.defaultValue()
        }
    )
}
