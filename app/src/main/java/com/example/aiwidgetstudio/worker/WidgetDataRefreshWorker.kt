package com.example.aiwidgetstudio.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aiwidgetstudio.data.datasource.DataSourceResolver
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
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
    private val dataSourceResolver: DataSourceResolver
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
                val updates = dataSourceResolver.resolveAll(definition.data.variables)

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

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetDataRefreshWorker>().build())
    }
}
