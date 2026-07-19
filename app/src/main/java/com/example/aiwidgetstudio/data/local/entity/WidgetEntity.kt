package com.example.aiwidgetstudio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WidgetEntity (
    @PrimaryKey
    val widgetId: String,
    val name: String,
    val dslJson: String,
    val originalPrompt: String,
    val createdAt: Long,
    val updatedAt: Long
)
