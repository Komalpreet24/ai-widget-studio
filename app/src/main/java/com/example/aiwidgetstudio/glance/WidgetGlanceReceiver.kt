package com.example.aiwidgetstudio.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.example.aiwidgetstudio.MainActivity
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

const val ACTION_WIDGET_PINNED = MainActivity.ACTION_WIDGET_PINNED

class WidgetGlanceReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WidgetGlanceAppWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_PINNED) {
            val widgetId = intent.getStringExtra(MainActivity.EXTRA_WIDGET_ID) ?: return
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val repository = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetRuntimeEntryPoint::class.java
            ).widgetRepository()
            runBlocking { repository.saveInstance(appWidgetId, widgetId) }
            CoroutineScope(Dispatchers.Main).launch {
                WidgetGlanceAppWidget().updateAll(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRuntimeEntryPoint::class.java
        ).widgetRepository()
        runBlocking {
            appWidgetIds.forEach { repository.deleteInstance(it) }
        }
    }
}
