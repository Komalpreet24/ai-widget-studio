package com.example.aiwidgetstudio.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetInstanceEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity
import kotlinx.coroutines.flow.Flow

data class WidgetWithState(
    @Embedded
    val widget: WidgetEntity,
    @Relation(
        parentColumn = "widgetId",
        entityColumn = "widgetId"
    )
    val state: WidgetStateEntity
)

@Dao
interface WidgetDao {

    @Transaction
    suspend fun createWidget(widget: WidgetEntity, state: WidgetStateEntity) {
        insertWidget(widget)
        insertState(state)
    }

    @Transaction
    suspend fun updateWidget(widget: WidgetEntity, state: WidgetStateEntity) {
        updateWidgetEntity(widget)
        updateState(state)
    }

    @Transaction
    suspend fun deleteWidgetIfUnplaced(widgetId: String): Boolean {
        if (getPlacementCount(widgetId) > 0) return false
        return deleteWidgetById(widgetId) > 0
    }

    @Insert
    suspend fun insertWidget(widget: WidgetEntity)

    @Insert
    suspend fun insertState(state: WidgetStateEntity)

    @Update
    suspend fun updateWidgetEntity(widget: WidgetEntity)

    @Update
    suspend fun updateState(state: WidgetStateEntity)

    @Query("DELETE FROM WidgetEntity WHERE widgetId = :widgetId")
    suspend fun deleteWidgetById(widgetId: String): Int

    @Query("SELECT * FROM WidgetEntity ORDER BY createdAt DESC")
    fun observeWidgets(): Flow<List<WidgetEntity>>

    @Query(
        """
        SELECT WidgetEntity.*,
        (SELECT COUNT(*) FROM WidgetInstanceEntity WHERE widgetId = WidgetEntity.widgetId) AS placementCount
        FROM WidgetEntity ORDER BY createdAt DESC
        """
    )
    fun observeWidgetListEntries(): Flow<List<WidgetListEntry>>

    @Query("SELECT * FROM WidgetEntity WHERE widgetId = :widgetId")
    suspend fun getWidgetById(widgetId: String): WidgetEntity?

    @Query("SELECT * FROM WidgetStateEntity WHERE widgetId = :widgetId")
    suspend fun getStateById(widgetId: String): WidgetStateEntity?

    @Transaction
    @Query("SELECT * FROM WidgetEntity WHERE widgetId = :widgetId")
    suspend fun getWidgetWithState(widgetId: String): WidgetWithState?

    @Transaction
    @Query("SELECT * FROM WidgetEntity ORDER BY createdAt DESC")
    suspend fun getAllWidgetsWithState(): List<WidgetWithState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInstance(instance: WidgetInstanceEntity)

    @Query("SELECT widgetId FROM WidgetInstanceEntity WHERE appWidgetId = :appWidgetId")
    suspend fun getWidgetId(appWidgetId: Int): String?

    @Query("SELECT appWidgetId FROM WidgetInstanceEntity WHERE widgetId = :widgetId")
    suspend fun getAppWidgetIds(widgetId: String): List<Int>

    @Query("SELECT COUNT(*) FROM WidgetInstanceEntity WHERE widgetId = :widgetId")
    suspend fun getPlacementCount(widgetId: String): Int

    @Query("DELETE FROM WidgetInstanceEntity WHERE appWidgetId = :appWidgetId")
    suspend fun deleteInstance(appWidgetId: Int)
}
