package com.example.aiwidgetstudio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aiwidgetstudio.data.local.dao.WidgetDao
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity

@Database(
    entities = [WidgetEntity::class, WidgetStateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WidgetDatabase : RoomDatabase() {

    abstract fun widgetDao(): WidgetDao

}
