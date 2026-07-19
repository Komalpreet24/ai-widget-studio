package com.example.aiwidgetstudio.data.datasource

import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.VariableDefinition
import com.example.aiwidgetstudio.domain.model.VariableValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSourceResolver @Inject constructor(
    private val usageStatsResolver: UsageStatsResolver,
    private val callLogResolver: CallLogResolver,
    private val calendarResolver: CalendarResolver
) {
    fun resolve(variable: VariableDefinition): VariableValue? {
        return when (val source = variable.source) {
            is DataSource.UsageStats -> usageStatsResolver.resolve(source)
            is DataSource.CallLog -> {
                val indexedMatch = Regex("_(caller|name)_(\\d+)$").find(variable.name)
                when {
                    indexedMatch != null ->
                        callLogResolver.resolveCallerAtIndex(source, indexedMatch.groupValues[2].toInt() - 1)
                    variable.name.endsWith("_caller") || variable.name.endsWith("_name") ->
                        callLogResolver.resolveCallerAtIndex(source, 0)
                    else -> callLogResolver.resolveCount(source)
                }
            }
            is DataSource.Calendar -> {
                if (variable.name.endsWith("_minutes") || variable.name.endsWith("_countdown"))
                    calendarResolver.resolveMinutesUntilNext(source)
                else
                    calendarResolver.resolveNextEventTitle(source)
            }
            DataSource.HealthSteps, null -> null
        }
    }

    fun resolveAll(variables: List<VariableDefinition>): Map<String, VariableValue> {
        return variables
            .filter { it.source != null }
            .mapNotNull { v -> resolve(v)?.let { v.name to it } }
            .toMap()
    }
}
