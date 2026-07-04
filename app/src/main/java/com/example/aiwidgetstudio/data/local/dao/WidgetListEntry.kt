package com.example.aiwidgetstudio.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.aiwidgetstudio.data.local.entity.WidgetEntity

data class WidgetListEntry(
    @Embedded val widget: WidgetEntity,
    @ColumnInfo(name = "placementCount") val placementCount: Int
)
