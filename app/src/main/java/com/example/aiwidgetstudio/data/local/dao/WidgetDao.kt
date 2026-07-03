package com.example.aiwidgetstudio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {

    @Transaction
    suspend fun createWidget(widget: WidgetEntity, state: WidgetStateEntity) {
        insertWidget(widget)
        insertState(state)
    }

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
            ORDER BY createdAt DESC
            """
    )
    fun observeWidgets(): Flow<List<WidgetEntity>>

    @Query(
    """
        SELECT * 
        FROM WidgetEntity 
        WHERE widgetId = :widgetId
        """
    )
    suspend fun getWidgetById(widgetId: String): WidgetEntity?

    @Insert
    suspend fun insertState(state: WidgetStateEntity)

    @Update
    suspend fun updateState(state: WidgetStateEntity)

    @Delete
    suspend fun deleteState(state: WidgetStateEntity)

    @Query(
        """
        SELECT * 
        FROM WidgetStateEntity 
        WHERE widgetId = :widgetId
        """
    )
    suspend fun getStateById(widgetId: String): WidgetStateEntity?
}
