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
        if (!hasPermission()) return VariableValue.StringValue("No permission")
        return try {
            val now = System.currentTimeMillis()
            val end = now + source.lookaheadMinutes * 60_000L
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            val title = cursor?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf { t -> t.isNotBlank() } else null
            } ?: "No upcoming events"
            VariableValue.StringValue(title)
        } catch (_: Exception) {
            VariableValue.StringValue("")
        }
    }

    fun resolveMinutesUntilNext(source: DataSource.Calendar): VariableValue.IntValue {
        if (!hasPermission()) return VariableValue.IntValue(-1)
        return try {
            val now = System.currentTimeMillis()
            val end = now + source.lookaheadMinutes * 60_000L
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            val dtStart = cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            } ?: -1L
            val minutes = if (dtStart > 0) ((dtStart - now) / 60_000L).toInt() else -1
            VariableValue.IntValue(minutes)
        } catch (_: Exception) {
            VariableValue.IntValue(-1)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
