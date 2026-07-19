package com.example.aiwidgetstudio.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.aiwidgetstudio.data.datasource.CalendarResolver
import com.example.aiwidgetstudio.data.datasource.CallLogResolver
import com.example.aiwidgetstudio.data.datasource.UsageStatsResolver
import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionRequest(
    val permission: String,
    val label: String,
    val rationale: String,
    val isSpecial: Boolean,           // true = must open Settings manually
    val settingsIntent: Intent? = null
)

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsResolver: UsageStatsResolver,
    private val callLogResolver: CallLogResolver,
    private val calendarResolver: CalendarResolver
) {
    fun missingPermissions(definition: WidgetDefinition): List<PermissionRequest> {
        val sources = definition.data.variables.mapNotNull { it.source }.toSet()
        val missing = mutableListOf<PermissionRequest>()

        if (sources.any { it is DataSource.UsageStats } && !usageStatsResolver.hasPermission()) {
            missing += PermissionRequest(
                permission = "PACKAGE_USAGE_STATS",
                label = "Usage access",
                rationale = "Required to show screen time data for apps.",
                isSpecial = true,
                settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }

        if (sources.any { it is DataSource.CallLog } && !callLogResolver.hasPermission()) {
            missing += PermissionRequest(
                permission = Manifest.permission.READ_CALL_LOG,
                label = "Call log access",
                rationale = "Required to show missed call information.",
                isSpecial = false
            )
        }

        if (sources.any { it is DataSource.Calendar } && !calendarResolver.hasPermission()) {
            missing += PermissionRequest(
                permission = Manifest.permission.READ_CALENDAR,
                label = "Calendar access",
                rationale = "Required to show upcoming events.",
                isSpecial = false
            )
        }

        return missing
    }
}
