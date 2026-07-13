package com.example.aiwidgetstudio.glance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import dagger.hilt.android.EntryPointAccessors

class WidgetGlanceAppWidget : GlanceAppWidget() {

    override val stateDefinition = WidgetGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRuntimeEntryPoint::class.java
        )
        val runtime = entryPoint.widgetRuntime()
        val repository = entryPoint.widgetRepository()
        val processor = entryPoint.widgetDslProcessor()
        val stateCodec = entryPoint.widgetStateCodec()

        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        var widgetId = repository.getWidgetId(appWidgetId)

        if (widgetId == null) {
            val allWidgets = repository.getAllWidgetsWithState()
            if (allWidgets.size == 1) {
                widgetId = allWidgets.first().widget.widgetId
                repository.saveInstance(appWidgetId, widgetId)
            } else {
                provideContent { WidgetGlanceContent.Fallback(widgetId = "") }
                return
            }
        }

        val resolvedId = widgetId
        val initial = runtime.loadWidget(resolvedId)
        if (initial == null) {
            provideContent { WidgetGlanceContent.Fallback(widgetId = resolvedId) }
            return
        }

        updateAppWidgetState(context, WidgetGlanceStateDefinition, id) {
            it.copy(widgetId = resolvedId, stateJson = stateCodec.encode(initial.state))
        }

        val stored = repository.getWidgetWithState(resolvedId)
        val definition = stored?.let {
            processor.process(it.widget.dslJson).getOrNull()?.definition
        }

        if (definition == null) {
            provideContent { WidgetGlanceContent.Fallback(widgetId = resolvedId) }
            return
        }

        provideContent {
            val glanceState = currentState<WidgetGlanceState>()
            val state = stateCodec.decode(glanceState.stateJson, definition)
            WidgetGlanceContent.Widget(
                context = context,
                widgetId = resolvedId,
                definition = definition,
                state = state
            )
        }
    }
}
