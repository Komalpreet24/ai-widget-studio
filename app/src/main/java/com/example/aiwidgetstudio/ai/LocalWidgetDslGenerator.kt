package com.example.aiwidgetstudio.ai

import com.example.aiwidgetstudio.engine.ProcessedWidget
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.presentation.OperationStatus
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LocalWidgetDslGenerator @Inject constructor(
    private val modelManager: ModelManager,
    private val processor: WidgetDslProcessor
) : WidgetGenerator {
    private val mutex = Mutex()
    private var engine: Engine? = null

    override fun generate(prompt: String): Flow<GenerationProgress> = flow {
        emit(GenerationProgress(status = OperationStatus.Loading))
        try {
            mutex.withLock {
                val modelPath = modelManager.activeModelPath()
                    ?: error("Import an on-device model in Settings first")

                closeEngine()
                engine = createEngine(modelPath).also { it.initialize() }

                val systemInstruction = buildSystemPrompt()
                val userMessage = buildUserPrompt(prompt)
                val conversation = engine!!.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(temperature = 0.2, topK = 20, topP = 0.9)
                    )
                )

                val builder = StringBuilder()
                conversation.use { activeConversation ->
                    activeConversation.sendMessageAsync(userMessage).collect { chunk ->
                        builder.append(chunk)
                        emit(GenerationProgress(partialText = builder.toString(), status = OperationStatus.Loading))
                    }
                }

                val firstPass = processGenerated(builder.toString())
                if (firstPass.isSuccess) {
                    emit(GenerationProgress(partialText = builder.toString(), status = OperationStatus.Success))
                    return@flow
                }

                val repairBuilder = StringBuilder()
                engine!!.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(temperature = 0.1, topK = 10, topP = 0.9)
                    )
                ).use { repairConversation ->
                    repairConversation.sendMessageAsync(
                        buildRepairPrompt(prompt, builder.toString(), firstPass.exceptionOrNull()?.message ?: "Invalid DSL")
                    ).collect { chunk ->
                        repairBuilder.append(chunk)
                        emit(GenerationProgress(partialText = repairBuilder.toString(), status = OperationStatus.Loading))
                    }
                }

                val repaired = processGenerated(repairBuilder.toString())
                emit(
                    GenerationProgress(
                        partialText = repairBuilder.toString(),
                        status = repaired.fold(
                            onSuccess = { OperationStatus.Success },
                            onFailure = { OperationStatus.Error(it.message ?: "Unable to generate valid widget DSL") }
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            closeEngine()
            emit(GenerationProgress(status = OperationStatus.Error("Model ran out of memory")))
        } catch (e: Exception) {
            closeEngine()
            emit(GenerationProgress(status = OperationStatus.Error(e.message ?: "Generation failed")))
        }
    }

    suspend fun invalidateEngine() {
        mutex.withLock { closeEngine() }
    }

    private fun processGenerated(raw: String): Result<ProcessedWidget> {
        val json = AiOutputExtractor.extractWidgetJson(raw)
            ?: return Result.failure(IllegalArgumentException("Model output did not contain JSON"))
        return processor.process(json)
    }

    private fun createEngine(modelPath: String): Engine = try {
        Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU()))
    } catch (_: Exception) {
        Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
    }

    private fun buildSystemPrompt() = """
        You generate Android home-screen widget definitions as one JSON object.
        Output only JSON. No markdown fences or commentary.
        dslVersion must be 1.
        Allowed variable types: INT, BOOL, STRING, DOUBLE.
        Allowed updatePolicy types: NONE, DAILY_RESET, PERIODIC.
        Allowed action types: INCREMENT, DECREMENT, RESET, SET_VALUE, TOGGLE, OPEN_APP, OPEN_URL.
        Allowed UI node types: COLUMN, ROW, BOX, CARD, TEXT, BUTTON, PROGRESS, SPACER, DIVIDER, ICON.
        Bindings must be exact placeholders like {{variableName}} with no expressions.
        Use only local variables. No lists, providers, network, or arithmetic.
    """.trimIndent()

    private fun buildUserPrompt(prompt: String) = """
        <<<USER_PROMPT>>>
        $prompt
        <<<END_USER_PROMPT>>>
        Return one complete widget DSL JSON object for dslVersion 1.
    """.trimIndent()

    private fun buildRepairPrompt(prompt: String, malformed: String, error: String) = """
        The previous output was invalid.
        Error: $error
        User prompt: $prompt
        Malformed output: $malformed
        Return one corrected widget DSL JSON object only.
    """.trimIndent()
}
