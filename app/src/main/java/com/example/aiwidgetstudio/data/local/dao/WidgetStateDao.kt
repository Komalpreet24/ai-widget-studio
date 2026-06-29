package com.example.aiwidgetstudio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity

@Dao
interface WidgetStateDao {

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
