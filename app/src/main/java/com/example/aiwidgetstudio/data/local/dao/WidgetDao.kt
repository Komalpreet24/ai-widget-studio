package com.example.aiwidgetstudio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity

@Dao
interface WidgetDao {

    @Insert
    suspend fun insertWidget(widget: WidgetEntity)

    @Update
    suspend fun updateWidget(widget: WidgetEntity)

    @Delete
    suspend fun deleteWidget(widget: WidgetEntity)

    @Query(
        """
            SELECT * 
            FROM WidgetEntity
            """
    )
    suspend fun getWidgets(): List<WidgetEntity>

    @Query(
    """
        SELECT * 
        FROM WidgetEntity 
        WHERE widgetId = :widgetId
        """
    )
    suspend fun getWidgetById(widgetId: String): WidgetEntity?
}
