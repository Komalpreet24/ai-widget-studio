package com.example.aiwidgetstudio.engine.state

import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class WidgetStateEngine @Inject constructor() {

    fun createInitialState(definition: WidgetDefinition): WidgetState = WidgetState(
        values = definition.data.variables.associate { variable ->
            val default = variable.defaultValue()
            variable.name to (constrain(default, variable) ?: default)
        }
    )

    fun resetIfDue(
        definition: WidgetDefinition,
        lastResetAt: Instant,
        now: Instant,
        zoneId: ZoneId
    ): WidgetReset? {
        val resetAt = when (val policy = definition.data.updatePolicy) {
            UpdatePolicy.None -> return null
            is UpdatePolicy.DailyReset -> latestDailyBoundary(policy, now, zoneId)
            is UpdatePolicy.Periodic -> latestPeriodicBoundary(policy, lastResetAt, now)
        } ?: return null

        if (!resetAt.isAfter(lastResetAt)) return null
        return WidgetReset(createInitialState(definition), resetAt)
    }

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

        val variable = definition.data.variables.findLast { it.name == target } ?: return currentState
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

    private fun latestDailyBoundary(
        policy: UpdatePolicy.DailyReset,
        now: Instant,
        zoneId: ZoneId
    ): Instant? {
        if (policy.hour !in 0..23 || policy.minute !in 0..59) return null

        val zonedNow = now.atZone(zoneId)
        val resetTime = LocalTime.of(policy.hour, policy.minute)
        val today = zonedNow.toLocalDate().atTime(resetTime).atZone(zoneId).toInstant()

        return if (today <= now) {
            today
        } else {
            zonedNow.toLocalDate().minusDays(1).atTime(resetTime).atZone(zoneId).toInstant()
        }
    }

    private fun latestPeriodicBoundary(
        policy: UpdatePolicy.Periodic,
        lastResetAt: Instant,
        now: Instant
    ): Instant? {
        if (policy.intervalMinutes <= 0 || now <= lastResetAt) return null

        val elapsedMinutes = Duration.between(lastResetAt, now).toMinutes()
        if (elapsedMinutes < policy.intervalMinutes) return null

        val completedMinutes = elapsedMinutes - (elapsedMinutes % policy.intervalMinutes)
        return runCatching {
            lastResetAt.plus(completedMinutes, ChronoUnit.MINUTES)
        }.getOrNull()
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
