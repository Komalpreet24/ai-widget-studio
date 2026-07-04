package com.example.aiwidgetstudio.glance

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import dagger.hilt.android.EntryPointAccessors

class WidgetGlanceAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRuntimeEntryPoint::class.java
        )
        val runtime = entryPoint.widgetRuntime()
        val repository = entryPoint.widgetRepository()

        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        var widgetId = repository.getWidgetId(appWidgetId)

        if (widgetId == null) {
            val allWidgets = repository.getAllWidgetsWithState()
            if (allWidgets.size == 1) {
                widgetId = allWidgets.first().widget.widgetId
                repository.saveInstance(appWidgetId, widgetId)
                Log.d("WidgetGlance", "Auto-mapped appWidgetId=$appWidgetId to widgetId=$widgetId")
            } else {
                Log.w("WidgetGlance", "No widgetId mapping for appWidgetId=$appWidgetId, ${allWidgets.size} widgets available")
                provideContent { WidgetGlanceContent.Fallback(widgetId = "") }
                return
            }
        }

        val runtimeWidget = runtime.loadWidget(widgetId)
        if (runtimeWidget == null) {
            Log.w("WidgetGlance", "loadWidget returned null for widgetId=$widgetId")
            provideContent { WidgetGlanceContent.Fallback(widgetId = widgetId) }
            return
        }

        provideContent {
            WidgetGlanceContent.Widget(
                context = context,
                widgetId = widgetId,
                definition = runtimeWidget.definition,
                state = runtimeWidget.state
            )
        }
    }
}
