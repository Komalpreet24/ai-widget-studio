package com.example.aiwidgetstudio.ai

import android.content.Context
import com.example.aiwidgetstudio.BuildConfig
import com.example.aiwidgetstudio.ai.GeneratorPreference
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.presentation.OperationStatus
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class GenerationProgress(
    val partialText: String = "",
    val status: OperationStatus = OperationStatus.Idle
)

@Singleton
class GeminiWidgetGenerator @Inject constructor(
    private val processor: WidgetDslProcessor,
    private val generatorPreference: GeneratorPreference,
    @ApplicationContext private val context: Context
) : WidgetGenerator {

    private val systemPrompt: String by lazy {
        context.assets.open("prompt_online.md").bufferedReader().readText()
    }

    // Cached once — label=package for all user-installed launchable apps
    private val installedAppsHint: String by lazy {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to (it.loadLabel(pm).toString()) }
            .distinctBy { it.first }
            .sortedBy { it.second }
            .joinToString("\n") { (pkg, label) -> "$label=$pkg" }
        "\n\nInstalled apps on this device (use exact package names from this list for USAGE_STATS source):\n$apps"
    }

    override fun generate(prompt: String, existingDsl: String?): Flow<GenerationProgress> = flow {
        emit(GenerationProgress(status = OperationStatus.Loading))
        try {
            val model = GenerativeModel(
                modelName = "gemini-3.5-flash",
                apiKey = generatorPreference.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY },
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topK = 20
                    topP = 0.9f
                    responseMimeType = "application/json"
                },
                systemInstruction = content { text(systemPrompt) },
                requestOptions = RequestOptions(apiVersion = "v1beta")
            )
            val themeHint = when (generatorPreference.widgetTheme) {
                WidgetTheme.LIGHT -> "\n\nTheme: LIGHT. Use backgroundColor=#FFFFFF, textColor=#1A1A1A for all nodes. Do not use dark colors."
                WidgetTheme.DARK -> "\n\nTheme: DARK. Use backgroundColor=#1C1C1E, textColor=#FFFFFF for all nodes. Do not use light colors."
                WidgetTheme.SYSTEM -> "\n\nTheme: SYSTEM (follows device). Do NOT hardcode backgroundColor or textColor on any node unless the user explicitly asked for a specific color. Leave them null so the app applies the correct theme colors automatically."
            }
            val appPackageHint = installedAppsHint
            val userMessage = if (existingDsl != null) {
                "Modify this existing widget DSL:\n$existingDsl\n\nChanges requested: $prompt$themeHint$appPackageHint"
            } else {
                "Create a widget for: $prompt$themeHint$appPackageHint"
            }
            val builder = StringBuilder()
            model.generateContentStream(content { text(userMessage) })
                .collect { chunk ->
                    chunk.text?.let {
                        builder.append(it)
                        emit(GenerationProgress(partialText = builder.toString(), status = OperationStatus.Loading))
                    }
                }
            val json = AiOutputExtractor.extractWidgetJson(builder.toString()) ?: builder.toString()
            val result = processor.process(json)
            emit(
                GenerationProgress(
                    partialText = json,
                    status = result.fold(
                        onSuccess = { OperationStatus.Success },
                        onFailure = { OperationStatus.Error(it.message ?: "Invalid widget DSL") }
                    )
                )
            )
        } catch (e: Exception) {
            emit(GenerationProgress(status = OperationStatus.Error(e.message ?: "Generation failed")))
        }
    }

}
