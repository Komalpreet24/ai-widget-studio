package com.example.aiwidgetstudio.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiwidgetstudio.data.local.database.WidgetDatabase
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetInstanceEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetDaoInstrumentedTest {

    private lateinit var database: WidgetDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WidgetDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createWidget_persistsWidgetAndStateTogether() {
        val dao = database.widgetDao()
        val widget = WidgetEntity("w1", "Test", "{}", 1L, 1L)
        val state = WidgetStateEntity("w1", """{"count":0}""", 1L)

        dao.createWidget(widget, state)

        val loaded = dao.getWidgetWithState("w1")
        assertEquals("Test", loaded?.widget?.name)
        assertEquals("""{"count":0}""", loaded?.state?.stateJson)
    }

    @Test
    fun deleteWidgetIfUnplaced_blocksWhenInstancesExist() {
        val dao = database.widgetDao()
        val widget = WidgetEntity("w1", "Test", "{}", 1L, 1L)
        val state = WidgetStateEntity("w1", "{}", 1L)
        dao.createWidget(widget, state)
        dao.saveInstance(WidgetInstanceEntity(42, "w1"))

        assertFalse(dao.deleteWidgetIfUnplaced("w1"))
        assertEquals(1, dao.getPlacementCount("w1"))
    }

    @Test
    fun migration1to2_addsLastResetAtAndInstanceTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldDb = context.getDatabasePath("migration-test.db")
        oldDb.parentFile?.mkdirs()
        if (oldDb.exists()) oldDb.delete()

        val db = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(oldDb.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE WidgetEntity (
                            widgetId TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            dslJson TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE WidgetStateEntity (
                            widgetId TEXT NOT NULL PRIMARY KEY,
                            stateJson TEXT NOT NULL,
                            FOREIGN KEY(widgetId) REFERENCES WidgetEntity(widgetId) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper(db)
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, WidgetDatabase::class.java, oldDb.name)
            .addMigrations(WidgetDatabase.MIGRATION_1_2)
            .build()

        migrated.openHelper.writableDatabase.query("PRAGMA table_info(WidgetStateEntity)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertTrue(columns.contains("lastResetAt"))
        }
        migrated.close()
        oldDb.delete()
    }
}
