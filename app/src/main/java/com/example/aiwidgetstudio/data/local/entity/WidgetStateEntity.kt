package com.example.aiwidgetstudio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = WidgetEntity::class,
            parentColumns = ["widgetId"],
            childColumns = ["widgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WidgetStateEntity (
    @PrimaryKey
    val widgetId: String,
    val stateJson: String,
    val lastResetAt: Long
)
