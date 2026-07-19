package com.example.aiwidgetstudio.domain.model

sealed interface DataSource {
    /** App screen time. Returns minutes as Int. */
    data class UsageStats(val packageName: String, val windowMinutes: Int = 1440) : DataSource

    /** Call log query. Returns missed call count as Int, most recent caller name as String. */
    data class CallLog(
        val filter: CallFilter = CallFilter.MISSED,
        val windowMinutes: Int = 60
    ) : DataSource

    /** Next calendar event. Returns event title as String, minutes until event as Int. */
    data class Calendar(val lookaheadMinutes: Int = 1440) : DataSource

    /** Step count today via Health Connect. Returns steps as Int. */
    object HealthSteps : DataSource

    enum class CallFilter { MISSED, ALL }
}
