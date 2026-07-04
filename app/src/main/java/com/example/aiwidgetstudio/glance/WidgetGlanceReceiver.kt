package com.example.aiwidgetstudio.glance

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

class WidgetGlanceReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WidgetGlanceAppWidget()

    override fun onDeleted(context: android.content.Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRuntimeEntryPoint::class.java
        ).widgetRepository()
        runBlocking {
            appWidgetIds.forEach { appWidgetId ->
                repository.deleteInstance(appWidgetId)
            }
        }
    }
}
