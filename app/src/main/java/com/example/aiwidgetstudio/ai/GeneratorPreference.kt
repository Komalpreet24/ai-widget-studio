package com.example.aiwidgetstudio.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class GeneratorMode { GEMINI, ON_DEVICE }

@Singleton
class GeneratorPreference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("generator_prefs", Context.MODE_PRIVATE)

    var mode: GeneratorMode
        get() = GeneratorMode.valueOf(prefs.getString(KEY_MODE, GeneratorMode.GEMINI.name)!!)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_API_KEY = "gemini_api_key"
    }
}
