package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetData
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.domain.model.WidgetMetadata
import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.VariableValue
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateEngineTest {

    private val engine = WidgetStateEngine()
    private val definition = WidgetDefinition(
        metadata = WidgetMetadata("Test"),
        data = WidgetData(
            updatePolicy = UpdatePolicy.DailyReset(hour = 6, minute = 30),
            variables = listOf(
                VariableDefinition.IntegerVariable("count", default = 0, min = 0, max = 10)
            )
        ),
        actions = listOf(
            WidgetAction.Increment("add", 1, "count"),
            WidgetAction.Decrement("sub", 1, "count"),
            WidgetAction.Reset("reset", "count")
        ),
        ui = UiNode.Text(com.example.aiwidgetstudio.domain.model.UiNodeStyle(), "x")
    )

    @Test
    fun createInitialState_usesDefaults() {
        val state = engine.createInitialState(definition)
        assertEquals(0, (state.values["count"] as VariableValue.IntValue).value)
    }

    @Test
    fun applyAction_incrementsAndClamps() {
        val initial = WidgetState(mapOf("count" to VariableValue.IntValue(9)))
        val updated = engine.applyAction(definition, initial, "add")
        assertEquals(10, (updated.values["count"] as VariableValue.IntValue).value)
    }

    @Test
    fun resetIfDue_triggersAfterDailyBoundary() {
        val zone = ZoneId.of("America/New_York")
        val lastReset = Instant.parse("2026-07-03T10:00:00Z")
        val now = Instant.parse("2026-07-04T11:00:00Z")
        val reset = engine.resetIfDue(definition, lastReset, now, zone)
        assertTrue(reset != null)
    }

    @Test
    fun resetIfDue_returnsNullWhenNotDue() {
        val zone = ZoneId.of("UTC")
        val lastReset = Instant.parse("2026-07-04T06:30:00Z")
        val now = Instant.parse("2026-07-04T07:00:00Z")
        assertNull(engine.resetIfDue(definition, lastReset, now, zone))
    }
}
