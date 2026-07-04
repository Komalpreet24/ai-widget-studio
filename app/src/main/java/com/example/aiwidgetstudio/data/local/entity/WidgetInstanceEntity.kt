package com.example.aiwidgetstudio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = WidgetEntity::class,
            parentColumns = ["widgetId"],
            childColumns = ["widgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("widgetId")]
)
data class WidgetInstanceEntity(
    @PrimaryKey
    val appWidgetId: Int,
    val widgetId: String
)
