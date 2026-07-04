package com.example.aiwidgetstudio.glance

import com.example.aiwidgetstudio.engine.state.WidgetState
import com.example.aiwidgetstudio.domain.model.VariableValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BindingResolverTest {

    private val state = WidgetState(
        mapOf(
            "waterCount" to VariableValue.IntValue(3),
            "dailyGoal" to VariableValue.IntValue(10),
            "label" to VariableValue.StringValue("Water")
        )
    )

    @Test
    fun resolveText_replacesBinding() {
        assertEquals("3", BindingResolver.resolveText("{{waterCount}}", state))
        assertEquals("Water", BindingResolver.resolveText("{{label}}", state))
    }

    @Test
    fun progressRatio_clampsToUnitInterval() {
        assertEquals(0.3f, BindingResolver.progressRatio("{{waterCount}}", "{{dailyGoal}}", state))
        assertEquals(0f, BindingResolver.progressRatio("3", "0", state))
    }
}
