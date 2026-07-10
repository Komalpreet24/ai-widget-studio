package com.example.aiwidgetstudio.ai

import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.presentation.OperationStatus
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
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
    private val preference: GeneratorPreference
) : WidgetGenerator {

    override fun generate(prompt: String): Flow<GenerationProgress> = flow {
        emit(GenerationProgress(status = OperationStatus.Loading))
        val apiKey = preference.geminiApiKey
        if (apiKey.isBlank()) {
            emit(GenerationProgress(status = OperationStatus.Error("Enter a Gemini API key in Settings")))
            return@flow
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topK = 20
                    topP = 0.9f
                    responseMimeType = "application/json"
                },
                systemInstruction = content { text(SYSTEM_PROMPT) }
            )
            val builder = StringBuilder()
            model.generateContentStream(content { text("Create a widget for: $prompt") })
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

    companion object {
        private val SYSTEM_PROMPT = """
            You are a widget DSL generator. Output ONLY valid JSON, no explanation, no markdown, no code blocks.

            Schema:
            {
              "dslVersion": 1,
              "metadata": { "name": "string" },
              "data": {
                "updatePolicy": { "type": "NONE" | "DAILY_RESET" | "PERIODIC", "hour": int, "minute": int, "intervalMinutes": int },
                "variables": [ { "name": "string", "type": "INT" | "BOOLEAN" | "STRING" | "DOUBLE", "default": value, "min": int, "max": int } ]
              },
              "actions": [
                { "id": "string", "type": "INCREMENT" | "DECREMENT" | "RESET" | "SET_VALUE" | "TOGGLE" | "OPEN_URL", "target": "variableName", "step": int, "value": any, "url": "string" }
              ],
              "ui": <UiNode>
            }

            UiNode types:
            - { "type": "COLUMN" | "ROW", "style": {}, "children": [<UiNode>] }
            - { "type": "CARD" | "BOX", "style": {}, "child": <UiNode> }
            - { "type": "TEXT", "style": {}, "value": "use {{variableName}} to reference variables" }
            - { "type": "BUTTON", "style": {}, "text": "string", "action": "actionId" }
            - { "type": "PROGRESS", "style": {}, "current": "{{variableName}}", "max": "{{variableName}} or number" }
            - { "type": "SPACER", "style": {}, "height": int, "width": int }
            - { "type": "DIVIDER", "style": {} }

            Style fields (all optional): textColor, backgroundColor, cornerRadius, padding, margin (int or {left,top,right,bottom}), alignment (START|CENTER|END), arrangement (START|CENTER|END), height, width

            Rules:
            - Every action id must be unique
            - Button action field must match an action id exactly
            - Variable names in {{}} must match a defined variable name exactly
            - Keep widgets simple and focused
        """.trimIndent()
    }
}
