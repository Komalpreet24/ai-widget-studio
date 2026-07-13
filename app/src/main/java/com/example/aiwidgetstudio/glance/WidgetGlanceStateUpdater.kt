package com.example.aiwidgetstudio.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetGlanceStateUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WidgetRepository
) {
    suspend fun pushState(widgetId: String, stateJson: String) {
        val appWidgetIds = repository.getAppWidgetIds(widgetId)
        if (appWidgetIds.isEmpty()) return

        val manager = GlanceAppWidgetManager(context)
        appWidgetIds.forEach { appWidgetId ->
            runCatching {
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, WidgetGlanceStateDefinition, glanceId) {
                    it.copy(widgetId = widgetId, stateJson = stateJson)
                }
                WidgetGlanceAppWidget().update(context, glanceId)
            }
        }
    }
}
