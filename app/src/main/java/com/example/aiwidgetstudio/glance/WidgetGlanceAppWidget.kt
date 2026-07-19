package com.example.aiwidgetstudio.glance

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.engine.state.WidgetState
import dagger.hilt.android.EntryPointAccessors

private sealed interface WidgetLoadResult {
    object Loading : WidgetLoadResult
    data class Fallback(val widgetId: String) : WidgetLoadResult
    data class Ready(
        val widgetId: String,
        val definition: WidgetDefinition,
        val state: WidgetState
    ) : WidgetLoadResult
}

class WidgetGlanceAppWidget : GlanceAppWidget() {

    override val stateDefinition = WidgetGlanceStateDefinition

    override val sizeMode = SizeMode.Single

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
        val dataSourceResolver = entryPoint.dataSourceResolver()

        provideContent {
            val widgetId by repository.observeWidgetId(appWidgetId).collectAsState(initial = null)

            val result by produceState<WidgetLoadResult>(
                initialValue = WidgetLoadResult.Loading,
                key1 = widgetId
            ) {
                val wid = widgetId
                if (wid == null) {
                    value = WidgetLoadResult.Loading
                    return@produceState
                }
                val initial = runtime.loadWidget(wid)
                if (initial == null) {
                    value = WidgetLoadResult.Fallback(wid)
                    return@produceState
                }
                val stored = repository.getWidgetWithState(wid)
                val definition = stored?.let {
                    processor.process(it.widget.dslJson).getOrNull()?.definition
                }
                if (definition == null) {
                    value = WidgetLoadResult.Fallback(wid)
                    return@produceState
                }

                // Resolve data sources immediately so first render has fresh data
                val updates = dataSourceResolver.resolveAll(definition.data.variables)
                val freshState = if (updates.isNotEmpty()) {
                    val merged = initial.state.copy(values = initial.state.values + updates)
                    runtime.setState(wid, merged)
                    merged
                } else initial.state
                updateAppWidgetState(context, WidgetGlanceStateDefinition, id) {
                    it.copy(
                        widgetId = wid,
                        stateJson = stateCodec.encode(freshState),
                        widgetSizeKey = definition.metadata.size.name
                    )
                }
                value = WidgetLoadResult.Ready(wid, definition, freshState)
            }

            when (val r = result) {
                is WidgetLoadResult.Loading -> WidgetGlanceContent.Loading()
                is WidgetLoadResult.Fallback -> WidgetGlanceContent.Fallback(widgetId = r.widgetId)
                is WidgetLoadResult.Ready -> {
                    val glanceState = currentState<WidgetGlanceState>()
                    val state = stateCodec.decode(glanceState.stateJson, r.definition)
                    WidgetGlanceContent.Widget(
                        context = context,
                        widgetId = r.widgetId,
                        definition = r.definition,
                        state = state
                    )
                }
            }
        }
    }
}
