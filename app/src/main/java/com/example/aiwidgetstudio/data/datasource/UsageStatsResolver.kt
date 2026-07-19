package com.example.aiwidgetstudio.data.datasource

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.VariableValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolve(source: DataSource.UsageStats): VariableValue.IntValue {
        if (!hasPermission()) return VariableValue.IntValue(0)
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - source.windowMinutes * 60_000L
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
            val totalMs = stats
                .filter { it.packageName == source.packageName }
                .sumOf { it.totalTimeInForeground }
            VariableValue.IntValue((totalMs / 60_000L).toInt())
        } catch (_: Exception) {
            VariableValue.IntValue(0)
        }
    }

    fun hasPermission(): Boolean = try {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        stats != null
    } catch (_: Exception) {
        false
    }
}
