package com.example.aiwidgetstudio.domain.model

sealed interface WidgetAction {
    val id: String

    data class Increment(override val id: String, val step: Int, val target: String) : WidgetAction
    data class Decrement(override val id: String, val step: Int, val target: String) : WidgetAction
    data class Reset(override val id: String, val target: String) : WidgetAction
    data class OpenApp(override val id: String) : WidgetAction
}
