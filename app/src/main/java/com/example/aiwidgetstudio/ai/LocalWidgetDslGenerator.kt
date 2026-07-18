package com.example.aiwidgetstudio.ai

import android.content.Context
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.presentation.OperationStatus
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalWidgetDslGenerator @Inject constructor(
    private val modelManager: ModelManager,
    private val processor: WidgetDslProcessor,
    @ApplicationContext private val context: Context
) : WidgetGenerator {
    private val mutex = Mutex()
    private var inference: LlmInference? = null
    private var loadedModelPath: String? = null
    private val systemPrompt: String by lazy {
        context.assets.open("prompt_offline.md").bufferedReader().readText()
    }

    suspend fun warmUp() {
        mutex.withLock {
            val modelPath = modelManager.activeModelPath() ?: return
            if (inference != null && loadedModelPath == modelPath) return
            runCatching { ensureInference(modelPath) }
        }
    }

    override fun generate(prompt: String, existingDsl: String?): Flow<GenerationProgress> = flow {
        emit(GenerationProgress(status = OperationStatus.Loading))
        try {
            mutex.withLock {
                val modelPath = modelManager.activeModelPath()
                    ?: error("Import an on-device model in Settings first")

                if (inference == null || loadedModelPath != modelPath) {
                    closeInference()
                    ensureInference(modelPath)
                }

                val userMessage = if (existingDsl != null) buildEditPrompt(prompt, existingDsl) else buildUserPrompt(prompt)
                val raw = runGeneration("$systemPrompt\n\n$userMessage")
                val json = AiOutputExtractor.extractWidgetJson(raw)
                    ?: error("On-device model output was not valid JSON. For complex widgets, try switching to a cloud model in Settings.")

                val result = processor.process(json)
                emit(GenerationProgress(
                    partialText = raw,
                    status = result.fold(
                        onSuccess = { OperationStatus.Success },
                        onFailure = { OperationStatus.Error("On-device model couldn't generate a valid widget. Try a simpler description or switch to a cloud model.") }
                    )
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            closeInference()
            emit(GenerationProgress(status = OperationStatus.Error("On-device model ran out of memory")))
        } catch (e: Exception) {
            closeInference()
            emit(GenerationProgress(status = OperationStatus.Error(e.message ?: "Generation failed")))
        }
    }

    suspend fun invalidateEngine() {
        mutex.withLock { closeInference() }
    }

    private suspend fun ensureInference(modelPath: String) {
        val newInference = withContext(Dispatchers.Default) {
            LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(2048)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
            )
        }
        inference = newInference
        loadedModelPath = modelPath
    }

    private suspend fun runGeneration(prompt: String): String = withContext(Dispatchers.Default) {
        LlmInferenceSession.createFromOptions(
            inference!!,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.2f)
                .setTopK(20)
                .setTopP(0.9f)
                .build()
        ).use { session ->
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    private fun closeInference() {
        inference?.close()
        inference = null
        loadedModelPath = null
    }

    private fun buildUserPrompt(prompt: String) =
        "Create a widget for: $prompt\nReturn one JSON object following the exact schema. dslVersion must be 1."

    private fun buildEditPrompt(changes: String, existingDsl: String) = """
        Modify the following widget DSL JSON based on the requested changes.
        Return the complete updated JSON only. dslVersion must be 1.
        Existing DSL:
        $existingDsl
        Changes requested: $changes
    """.trimIndent()
}
