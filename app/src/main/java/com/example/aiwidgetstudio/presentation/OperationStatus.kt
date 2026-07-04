package com.example.aiwidgetstudio.presentation

sealed interface OperationStatus {
    data object Idle : OperationStatus
    data object Loading : OperationStatus
    data object Success : OperationStatus
    data class Error(val message: String) : OperationStatus
}
