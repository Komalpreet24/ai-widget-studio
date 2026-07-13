package com.example.aiwidgetstudio.ai

import kotlinx.coroutines.flow.Flow

interface WidgetGenerator {
    fun generate(prompt: String, existingDsl: String? = null): Flow<GenerationProgress>
}
