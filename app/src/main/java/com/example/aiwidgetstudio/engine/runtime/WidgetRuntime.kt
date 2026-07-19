package com.example.aiwidgetstudio.engine.runtime

import com.example.aiwidgetstudio.data.local.entity.WidgetEntity
import com.example.aiwidgetstudio.data.local.entity.WidgetStateEntity
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.engine.ProcessedWidget
import com.example.aiwidgetstudio.engine.WidgetDslProcessor
import com.example.aiwidgetstudio.engine.state.WidgetState
import com.example.aiwidgetstudio.engine.state.WidgetStateCodec
import com.example.aiwidgetstudio.engine.state.WidgetStateEngine
import com.example.aiwidgetstudio.engine.validator.WidgetValidatorWarning
import com.example.aiwidgetstudio.glance.WidgetGlanceStateUpdater
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CreatedWidget(
    val widgetId: String,
    val warnings: List<WidgetValidatorWarning>
)

data class RuntimeWidget(
    val definition: WidgetDefinition,
    val state: WidgetState
)

@Singleton
class WidgetRuntime @Inject constructor(
    private val processor: WidgetDslProcessor,
    private val stateEngine: WidgetStateEngine,
    private val stateCodec: WidgetStateCodec,
    private val repository: WidgetRepository,
    private val glanceStateUpdater: WidgetGlanceStateUpdater,
    private val clock: Clock
) {

    private val stateMutex = Mutex()

    fun processDsl(dslJson: String): Result<ProcessedWidget> {
        return processor.process(dslJson)
    }

    suspend fun createWidget(dslJson: String, originalPrompt: String = ""): Result<CreatedWidget> {
        val processResult = processor.process(dslJson)
        val processed = processResult.getOrNull()
        if (processed == null) {
            return Result.failure(processingError(processResult))
        }

        val widgetId = UUID.randomUUID().toString()
        val timestamp = clock.millis()
        val state = stateEngine.createInitialState(processed.definition)
        val widget = WidgetEntity(
            widgetId = widgetId,
            name = processed.definition.metadata.name,
            dslJson = dslJson,
            originalPrompt = originalPrompt,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        val stateEntity = WidgetStateEntity(
            widgetId = widgetId,
            stateJson = stateCodec.encode(state),
            lastResetAt = timestamp
        )

        return try {
            repository.createWidget(widget, stateEntity)
            Result.success(CreatedWidget(widgetId, processed.warnings))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun updateWidgetDefinition(
        widgetId: String,
        dslJson: String
    ): Result<ProcessedWidget> {
        val processResult = processor.process(dslJson)
        val processed = processResult.getOrNull()
        if (processed == null) {
            return Result.failure(processingError(processResult))
        }

        return stateMutex.withLock {
            try {
                val stored = repository.getWidgetWithState(widgetId)
                    ?: return@withLock Result.failure(widgetNotFound(widgetId))

                val previousDefinition = processor.process(stored.widget.dslJson)
                    .getOrNull()
                    ?.definition
                val policyChanged = previousDefinition?.data?.updatePolicy !=
                    processed.definition.data.updatePolicy
                val updatedState = stateCodec.decode(
                    stored.state.stateJson,
                    processed.definition
                )
                val timestamp = clock.millis()
                val updatedWidget = stored.widget.copy(
                    name = processed.definition.metadata.name,
                    dslJson = dslJson,
                    updatedAt = timestamp
                )
                val updatedStateEntity = stored.state.copy(
                    stateJson = stateCodec.encode(updatedState),
                    lastResetAt = if (policyChanged) timestamp else stored.state.lastResetAt
                )

                repository.updateWidget(updatedWidget, updatedStateEntity)
                glanceStateUpdater.pushState(widgetId, stateCodec.encode(updatedState))
                Result.success(processed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    suspend fun loadWidget(widgetId: String): RuntimeWidget? {
        return stateMutex.withLock {
            try {
                val stored = repository.getWidgetWithState(widgetId) ?: return@withLock null
                val processed = processor.process(stored.widget.dslJson).getOrNull()
                    ?: return@withLock null
                val state = stateCodec.decode(stored.state.stateJson, processed.definition)
                val reset = stateEngine.resetIfDue(
                    definition = processed.definition,
                    lastResetAt = Instant.ofEpochMilli(stored.state.lastResetAt),
                    now = clock.instant(),
                    zoneId = clock.zone
                )

                if (reset != null) {
                    repository.updateState(
                        stored.state.copy(
                            stateJson = stateCodec.encode(reset.state),
                            lastResetAt = reset.resetAt.toEpochMilli()
                        )
                    )
                    RuntimeWidget(processed.definition, reset.state)
                } else {
                    val repairedStateJson = stateCodec.encode(state)
                    if (repairedStateJson != stored.state.stateJson) {
                        repository.updateState(stored.state.copy(stateJson = repairedStateJson))
                    }
                    RuntimeWidget(processed.definition, state)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
        }
    }

    suspend fun applyAction(widgetId: String, actionId: String): Boolean {
        return stateMutex.withLock {
            try {
                val stored = repository.getWidgetWithState(widgetId) ?: return@withLock false
                val processed = processor.process(stored.widget.dslJson).getOrNull()
                    ?: return@withLock false
                val decodedState = stateCodec.decode(stored.state.stateJson, processed.definition)
                val reset = stateEngine.resetIfDue(
                    definition = processed.definition,
                    lastResetAt = Instant.ofEpochMilli(stored.state.lastResetAt),
                    now = clock.instant(),
                    zoneId = clock.zone
                )
                val stateBeforeAction = reset?.state ?: decodedState
                val stateAfterAction = stateEngine.applyAction(
                    processed.definition,
                    stateBeforeAction,
                    actionId
                )
                val stateWasRepaired = stateCodec.encode(decodedState) != stored.state.stateJson
                val stateChanged = reset != null || stateAfterAction != decodedState || stateWasRepaired

                if (stateChanged) {
                    val newStateJson = stateCodec.encode(stateAfterAction)
                    repository.updateState(
                        stored.state.copy(
                            stateJson = newStateJson,
                            lastResetAt = reset?.resetAt?.toEpochMilli()
                                ?: stored.state.lastResetAt
                        )
                    )
                    glanceStateUpdater.pushState(widgetId, newStateJson)
                }

                stateChanged
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun resetIfDue(widgetId: String): Boolean {
        return stateMutex.withLock {
            try {
                val stored = repository.getWidgetWithState(widgetId) ?: return@withLock false
                val processed = processor.process(stored.widget.dslJson).getOrNull()
                    ?: return@withLock false
                val reset = stateEngine.resetIfDue(
                    definition = processed.definition,
                    lastResetAt = Instant.ofEpochMilli(stored.state.lastResetAt),
                    now = clock.instant(),
                    zoneId = clock.zone
                ) ?: return@withLock false

                repository.updateState(
                    stored.state.copy(
                        stateJson = stateCodec.encode(reset.state),
                        lastResetAt = reset.resetAt.toEpochMilli()
                    )
                )
                glanceStateUpdater.pushState(widgetId, stateCodec.encode(reset.state))
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun setState(widgetId: String, newState: WidgetState) {
        stateMutex.withLock {
            try {
                val stored = repository.getWidgetWithState(widgetId) ?: return@withLock
                val newStateJson = stateCodec.encode(newState)
                repository.updateState(stored.state.copy(stateJson = newStateJson))
                glanceStateUpdater.pushState(widgetId, newStateJson)
            } catch (_: Exception) { }
        }
    }

    suspend fun deleteWidget(widgetId: String): Boolean {
        return stateMutex.withLock {
            repository.deleteWidget(widgetId)
        }
    }

    private fun processingError(result: Result<ProcessedWidget>): Throwable {
        return result.exceptionOrNull()
            ?: IllegalArgumentException("Unable to process widget DSL")
    }

    private fun widgetNotFound(widgetId: String): NoSuchElementException {
        return NoSuchElementException("Widget not found: $widgetId")
    }
}
