package com.example.aiwidgetstudio.di

import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import com.example.aiwidgetstudio.glance.WidgetRefresh
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRuntimeEntryPoint {
    fun widgetRuntime(): WidgetRuntime
    fun widgetRefresh(): WidgetRefresh
    fun widgetRepository(): WidgetRepository
}
