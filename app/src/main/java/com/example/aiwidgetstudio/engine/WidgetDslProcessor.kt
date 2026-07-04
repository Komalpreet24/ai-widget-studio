package com.example.aiwidgetstudio.engine

import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.engine.parser.WidgetDslParser
import com.example.aiwidgetstudio.engine.parser.mapper.WidgetDslMapper
import com.example.aiwidgetstudio.engine.validator.WidgetValidator
import com.example.aiwidgetstudio.engine.validator.WidgetValidatorWarning
import javax.inject.Inject

data class ProcessedWidget(
    val definition: WidgetDefinition,
    val warnings: List<WidgetValidatorWarning>
)

class WidgetDslProcessor @Inject constructor(
    private val parser: WidgetDslParser,
    private val mapper: WidgetDslMapper,
    private val validator: WidgetValidator
) {

    fun process(rawJson: String): Result<ProcessedWidget> {
        val parseResult = parser.parse(rawJson)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
                ?: IllegalArgumentException("Unable to parse widget DSL")
            return Result.failure(error)
        }

        val dto = parseResult.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Widget DSL is empty"))
        if (dto.dslVersion != SUPPORTED_DSL_VERSION) {
            return Result.failure(
                IllegalArgumentException("Unsupported or missing DSL version: ${dto.dslVersion}")
            )
        }

        val definition = mapper.map(dto)
        val warnings = validator.validate(definition)

        return Result.success(ProcessedWidget(definition, warnings))
    }

    private companion object {
        const val SUPPORTED_DSL_VERSION = 1
    }
}
