package com.example.aiwidgetstudio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aiwidgetstudio.data.local.dao.WidgetDao
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetInstanceEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity

@Database(
    entities = [WidgetEntity::class, WidgetStateEntity::class, WidgetInstanceEntity::class],
    version = 2,
    exportSchema = true
)
abstract class WidgetDatabase : RoomDatabase() {

    abstract fun widgetDao(): WidgetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE WidgetStateEntity ADD COLUMN lastResetAt INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE WidgetStateEntity SET lastResetAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS WidgetInstanceEntity (
                        appWidgetId INTEGER NOT NULL,
                        widgetId TEXT NOT NULL,
                        PRIMARY KEY(appWidgetId),
                        FOREIGN KEY(widgetId) REFERENCES WidgetEntity(widgetId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_WidgetInstanceEntity_widgetId ON WidgetInstanceEntity(widgetId)"
                )
            }
        }
    }
}
