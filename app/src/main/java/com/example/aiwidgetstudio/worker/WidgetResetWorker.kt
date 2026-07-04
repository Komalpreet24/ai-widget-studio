package com.example.aiwidgetstudio.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.UpdatePolicy
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetResetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WidgetRepository,
    private val processor: WidgetDslProcessor,
    private val runtime: WidgetRuntime
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgets = repository.getAllWidgetsWithState()
        widgets.forEach { stored ->
            runCatching {
                val processed = processor.process(stored.widget.dslJson).getOrNull() ?: return@runCatching
                val policy = processed.definition.data.updatePolicy
                if (policy is UpdatePolicy.None) return@runCatching
                runtime.resetIfDue(stored.widget.widgetId)
            }
        }
        return Result.success()
    }
}

object ResetWorkScheduler {

    private const val WORK_NAME = "widget-reset-worker"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetResetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
