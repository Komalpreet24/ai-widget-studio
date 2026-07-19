package com.example.aiwidgetstudio.di

import com.example.aiwidgetstudio.ai.GeneratorPreference
import com.example.aiwidgetstudio.data.datasource.DataSourceResolver
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import com.example.aiwidgetstudio.engine.state.WidgetStateCodec
import com.example.aiwidgetstudio.glance.WidgetGlanceStateUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRuntimeEntryPoint {
    fun widgetRuntime(): WidgetRuntime
    fun widgetRepository(): WidgetRepository
    fun widgetStateCodec(): WidgetStateCodec
    fun widgetDslProcessor(): WidgetDslProcessor
    fun widgetGlanceStateUpdater(): WidgetGlanceStateUpdater
    fun generatorPreference(): GeneratorPreference
    fun dataSourceResolver(): DataSourceResolver
}
