package com.example.aiwidgetstudio.data.repository

import com.example.aiwidgetstudio.data.local.dao.WidgetDao
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity

class WidgetRepository(
    private val dao: WidgetDao
) {

    suspend fun createWidget(widget: WidgetEntity, state: WidgetStateEntity) =
        dao.createWidget(widget, state)

    suspend fun deleteWidget(widget: WidgetEntity) = dao.deleteWidget(widget)

    suspend fun getWidgetById(id: String) = dao.getWidgetById(id)

    suspend fun getAllWidgets() = dao.getAllWidgets()

}