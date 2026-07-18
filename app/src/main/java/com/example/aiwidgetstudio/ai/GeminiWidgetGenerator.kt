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
            val userMessage = if (existingDsl != null) {
                "Modify this existing widget DSL:\n$existingDsl\n\nChanges requested: $prompt"
            } else {
                "Create a widget for: $prompt"
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
