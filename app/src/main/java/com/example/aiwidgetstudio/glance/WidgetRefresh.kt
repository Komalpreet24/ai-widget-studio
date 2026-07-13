package com.example.aiwidgetstudio.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefresh @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WidgetRepository
) {
    suspend fun refreshWidget(widgetId: String) {
        val appWidgetIds = repository.getAppWidgetIds(widgetId)
        if (appWidgetIds.isEmpty()) return
        WidgetGlanceAppWidget().updateAll(context)
    }
}
