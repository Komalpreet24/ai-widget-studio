package com.example.aiwidgetstudio.data.repository

import com.example.aiwidgetstudio.data.local.dao.WidgetDao
import com.example.aiwidgetstudio.data.local.dao.WidgetListEntry
import com.example.aiwidgetstudio.data.local.dao.WidgetWithState
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetInstanceEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WidgetRepository @Inject constructor(
    private val dao: WidgetDao
) {

    suspend fun createWidget(widget: WidgetEntity, state: WidgetStateEntity) {
        dao.createWidget(widget, state)
    }

    suspend fun updateWidget(widget: WidgetEntity, state: WidgetStateEntity) {
        dao.updateWidget(widget, state)
    }

    suspend fun deleteWidget(widgetId: String): Boolean {
        return dao.deleteWidgetIfUnplaced(widgetId)
    }

    fun observeWidgets(): Flow<List<WidgetEntity>> {
        return dao.observeWidgets()
    }

    fun observeWidgetListEntries(): Flow<List<WidgetListEntry>> {
        return dao.observeWidgetListEntries()
    }

    fun observeWidgetWithState(widgetId: String): kotlinx.coroutines.flow.Flow<WidgetWithState?> {
        return dao.observeWidgetWithState(widgetId)
    }

    suspend fun getWidgetWithState(widgetId: String): WidgetWithState? {
        return dao.getWidgetWithState(widgetId)
    }

    suspend fun getAllWidgetsWithState(): List<WidgetWithState> {
        return dao.getAllWidgetsWithState()
    }

    suspend fun updateState(state: WidgetStateEntity) {
        dao.updateState(state)
    }

    suspend fun saveInstance(appWidgetId: Int, widgetId: String) {
        dao.saveInstance(WidgetInstanceEntity(appWidgetId, widgetId))
    }

    suspend fun getWidgetId(appWidgetId: Int): String? {
        return dao.getWidgetId(appWidgetId)
    }

    suspend fun getAppWidgetIds(widgetId: String): List<Int> {
        return dao.getAppWidgetIds(widgetId)
    }

    suspend fun getPlacementCount(widgetId: String): Int {
        return dao.getPlacementCount(widgetId)
    }

    suspend fun deleteInstance(appWidgetId: Int) {
        dao.deleteInstance(appWidgetId)
    }
}
