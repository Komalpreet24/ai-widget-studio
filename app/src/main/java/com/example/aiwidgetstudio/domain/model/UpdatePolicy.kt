package com.example.aiwidgetstudio.domain.model

sealed interface UpdatePolicy {
    data object None: UpdatePolicy
    data class Periodic(val intervalMinutes: Long) : UpdatePolicy
    data class DailyReset(val hour: Int, val minute: Int) : UpdatePolicy
}
