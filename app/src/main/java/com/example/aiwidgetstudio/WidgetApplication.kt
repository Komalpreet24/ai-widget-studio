package com.example.aiwidgetstudio

import android.app.Application
import androidx.room.Room
import com.example.aiwidgetstudio.data.local.database.WidgetDatabase
import com.example.aiwidgetstudio.data.repository.WidgetRepository

class WidgetApplication: Application() {

    val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            WidgetDatabase::class.java,
            "widget.db"
        ).build()
    }

    val repository by lazy {
        WidgetRepository(
            database.widgetDao()
        )
    }

}