package com.example.aiwidgetstudio.data.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.VariableValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveNextEventTitle(source: DataSource.Calendar): VariableValue.StringValue {
        if (!hasPermission()) return VariableValue.StringValue("")
        return try {
            val now = System.currentTimeMillis()
            val end = now + source.lookaheadMinutes * 60_000L
            val uri = android.net.Uri.withAppendedPath(
                CalendarContract.Instances.CONTENT_URI, "$now/$end"
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            val title = cursor?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf { t -> t.isNotBlank() } else null
            } ?: ""
            VariableValue.StringValue(title)
        } catch (_: Exception) {
            VariableValue.StringValue("")
        }
    }

    fun resolveMinutesUntilNext(source: DataSource.Calendar): VariableValue.IntValue {
        if (!hasPermission()) return VariableValue.IntValue(0)
        return try {
            val now = System.currentTimeMillis()
            val end = now + source.lookaheadMinutes * 60_000L
            val uri = android.net.Uri.withAppendedPath(
                CalendarContract.Instances.CONTENT_URI, "$now/$end"
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN),
                null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            val begin = cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
            val minutes = if (begin > 0) ((begin - now) / 60_000L).toInt().coerceAtLeast(0) else 0
            VariableValue.IntValue(minutes)
        } catch (_: Exception) {
            VariableValue.IntValue(0)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
