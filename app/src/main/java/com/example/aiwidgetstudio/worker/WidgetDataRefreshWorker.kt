package com.example.aiwidgetstudio.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aiwidgetstudio.data.datasource.CalendarResolver
import com.example.aiwidgetstudio.data.datasource.CallLogResolver
import com.example.aiwidgetstudio.data.datasource.UsageStatsResolver
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import com.example.aiwidgetstudio.engine.state.WidgetStateCodec
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetDataRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WidgetRepository,
    private val processor: WidgetDslProcessor,
    private val runtime: WidgetRuntime,
    private val stateCodec: WidgetStateCodec,
    private val usageStatsResolver: UsageStatsResolver,
    private val callLogResolver: CallLogResolver,
    private val calendarResolver: CalendarResolver
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgets = repository.getAllWidgetsWithState()
        widgets.forEach { stored ->
            runCatching {
                val processed = processor.process(stored.widget.dslJson).getOrNull() ?: return@runCatching
                val definition = processed.definition

                // 1. Reset if due
                val policy = definition.data.updatePolicy
                if (policy !is UpdatePolicy.None) {
                    runtime.resetIfDue(stored.widget.widgetId)
                }

                // 2. Resolve data sources
                val sourcedVariables = definition.data.variables.filter { it.source != null }
                if (sourcedVariables.isEmpty()) return@runCatching

                val currentState = stateCodec.decode(stored.state.stateJson, definition)
                val updates = mutableMapOf<String, VariableValue>()

                sourcedVariables.forEach { variable ->
                    when (val source = variable.source) {
                        is DataSource.UsageStats ->
                            updates[variable.name] = usageStatsResolver.resolve(source)
                        is DataSource.CallLog -> {
                            // name ending in "_count" or "_caller" determines which value
                            if (variable.name.endsWith("_caller") || variable.name.endsWith("_name")) {
                                updates[variable.name] = callLogResolver.resolveLatestCaller(source)
                            } else {
                                updates[variable.name] = callLogResolver.resolveCount(source)
                            }
                        }
                        is DataSource.Calendar -> {
                            if (variable.name.endsWith("_minutes") || variable.name.endsWith("_countdown")) {
                                updates[variable.name] = calendarResolver.resolveMinutesUntilNext(source)
                            } else {
                                updates[variable.name] = calendarResolver.resolveNextEventTitle(source)
                            }
                        }
                        DataSource.HealthSteps -> { /* Health Connect — Phase 5 */ }
                        null -> Unit
                    }
                }

                if (updates.isNotEmpty()) {
                    val newState = currentState.copy(values = currentState.values + updates)
                    runtime.setState(stored.widget.widgetId, newState)
                }
            }
        }
        return Result.success()
    }
}

object RefreshWorkScheduler {
    private const val WORK_NAME = "widget-data-refresh"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetDataRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
