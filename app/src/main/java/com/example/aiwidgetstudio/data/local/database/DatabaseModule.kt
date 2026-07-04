package com.example.aiwidgetstudio.data.local.database

import android.content.Context
import androidx.room.Room
import com.example.aiwidgetstudio.data.local.dao.WidgetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WidgetDatabase =
        Room.databaseBuilder(context, WidgetDatabase::class.java, "widget.db")
            .addMigrations(WidgetDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideWidgetDao(database: WidgetDatabase): WidgetDao = database.widgetDao()
}
