package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.WidgetData
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.domain.model.WidgetMetadata
import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableValue
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStateCodecTest {

    private val codec = WidgetStateCodec()
    private val definition = WidgetDefinition(
        metadata = WidgetMetadata("Codec"),
        data = WidgetData(
            updatePolicy = UpdatePolicy.None,
            variables = listOf(
                VariableDefinition.IntegerVariable("count", 1, 0, 5),
                VariableDefinition.BooleanVariable("enabled", false)
            )
        ),
        actions = emptyList(),
        ui = UiNode.Text(com.example.aiwidgetstudio.domain.model.UiNodeStyle(), "x")
    )

    @Test
    fun roundTrip_preservesValues() {
        val state = WidgetState(
            mapOf(
                "count" to VariableValue.IntValue(3),
                "enabled" to VariableValue.BooleanValue(true)
            )
        )
        val decoded = codec.decode(codec.encode(state), definition)
        assertEquals(state, decoded)
    }

    @Test
    fun decode_repairsInvalidJson() {
        val decoded = codec.decode("{not-json", definition)
        assertEquals(1, (decoded.values["count"] as VariableValue.IntValue).value)
        assertEquals(false, (decoded.values["enabled"] as VariableValue.BooleanValue).value)
    }

    @Test
    fun decode_clampsOutOfRangeNumbers() {
        val decoded = codec.decode("""{"count":99}""", definition)
        assertEquals(5, (decoded.values["count"] as VariableValue.IntValue).value)
    }
}
