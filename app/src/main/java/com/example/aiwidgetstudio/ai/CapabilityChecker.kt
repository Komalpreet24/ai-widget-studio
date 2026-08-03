package com.example.aiwidgetstudio.ai

import com.example.aiwidgetstudio.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import javax.inject.Inject
import javax.inject.Singleton

data class CapabilityResult(
    val feasible: Boolean,
    val warning: String?,
    val suggestion: String?
)

@Singleton
class CapabilityChecker @Inject constructor(
    private val generatorPreference: GeneratorPreference
) {
    // Keywords that indicate features the DSL engine cannot support
    // Note: call log, calendar, sms-count are supported via data sources — not blocked
    private val blocklist = listOf(
        "camera", "photo", "video", "microphone", "audio", "record",
        "gps", "location", "map", "navigation",
        "bluetooth", "wifi", "nfc",
        "notification", "alarm", "reminder",
        "payment", "purchase", "buy",
        "login", "sign in", "authenticate",
        "download", "upload", "file manager",
        "3d", "animation", "gesture", "swipe",
        "live wallpaper", "always on display"
    )

    /** Fast on-device check — no network call. Returns a result immediately. */
    fun checkOnDevice(prompt: String): CapabilityResult {
        val lower = prompt.lowercase()
        val hit = blocklist.firstOrNull { lower.contains(it) } ?: return CapabilityResult(true, null, null)
        return CapabilityResult(
            feasible = false,
            warning = "Widgets can't access \"$hit\" — home screen widgets are static UI with counters, text, and buttons only.",
            suggestion = simpleSuggestion(hit)
        )
    }

    /** Gemini feasibility call — returns null if the API call fails (fail-open). */
    suspend fun checkWithGemini(prompt: String): CapabilityResult? {
        return try {
            val systemMsg = """
                You are a widget capability checker. Android home screen widgets can only show:
                static text, counters, buttons that increment/decrement values, progress bars, and open URLs or the app.
                They CANNOT access camera, GPS, microphone, contacts, notifications, payments, or run animations.
                
                Reply with JSON only: {"feasible": true/false, "warning": "...", "suggestion": "..."}
                If feasible, set warning and suggestion to null.
                Keep warning under 15 words. Keep suggestion under 12 words.
            """.trimIndent()
            val response = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = generatorPreference.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY },
                systemInstruction = content { text(systemMsg) }
            ).generateContent(content { text("Widget prompt: $prompt") })
            val text = response.text ?: return null
            val json = org.json.JSONObject(text.trim().removePrefix("```json").removeSuffix("```").trim())
            CapabilityResult(
                feasible = json.optBoolean("feasible", true),
                warning = json.optString("warning").takeIf { it.isNotBlank() && it != "null" },
                suggestion = json.optString("suggestion").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (_: Exception) {
            null // fail-open: don't block the user if the check fails
        }
    }

    private fun simpleSuggestion(hit: String): String = when {
        hit.contains("camera") || hit.contains("photo") -> "Try: a photo counter or daily log instead"
        hit.contains("location") || hit.contains("gps") || hit.contains("map") -> "Try: a manual location note widget instead"
        hit.contains("notification") || hit.contains("alarm") || hit.contains("reminder") -> "Try: a countdown timer or task checklist instead"
        hit.contains("contact") -> "Try: a quick-dial button that opens the app instead"
        hit.contains("payment") || hit.contains("purchase") -> "Try: a spending tracker with manual +/- buttons instead"
        else -> "Try: a simple counter or text display widget instead"
    }
}
