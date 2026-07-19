package com.example.aiwidgetstudio.data.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.aiwidgetstudio.domain.model.DataSource
import com.example.aiwidgetstudio.domain.model.VariableValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Returns count of matching calls as IntValue, most recent caller name as StringValue. */
    fun resolveCount(source: DataSource.CallLog): VariableValue.IntValue {
        if (!hasPermission()) return VariableValue.IntValue(0)
        return try {
            val since = System.currentTimeMillis() - source.windowMinutes * 60_000L
            val typeFilter = if (source.filter == DataSource.CallFilter.MISSED)
                CallLog.Calls.MISSED_TYPE else null
            val selection = buildString {
                append("${CallLog.Calls.DATE} >= ?")
                if (typeFilter != null) append(" AND ${CallLog.Calls.TYPE} = $typeFilter")
            }
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                selection,
                arrayOf(since.toString()),
                null
            )
            val count = cursor?.use { it.count } ?: 0
            VariableValue.IntValue(count)
        } catch (_: Exception) {
            VariableValue.IntValue(0)
        }
    }

    fun resolveLatestCaller(source: DataSource.CallLog): VariableValue.StringValue {
        if (!hasPermission()) return VariableValue.StringValue("")
        return try {
            val since = System.currentTimeMillis() - source.windowMinutes * 60_000L
            val typeFilter = if (source.filter == DataSource.CallFilter.MISSED)
                CallLog.Calls.MISSED_TYPE else null
            val selection = buildString {
                append("${CallLog.Calls.DATE} >= ?")
                if (typeFilter != null) append(" AND ${CallLog.Calls.TYPE} = $typeFilter")
            }
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
                selection,
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )
            val name = cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.takeIf { n -> n.isNotBlank() } ?: it.getString(1) ?: ""
                } else ""
            } ?: ""
            VariableValue.StringValue(name)
        } catch (_: Exception) {
            VariableValue.StringValue("")
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
}
