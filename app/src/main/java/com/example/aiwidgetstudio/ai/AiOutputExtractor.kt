package com.example.aiwidgetstudio.ai

object AiOutputExtractor {

    fun stripMarkdownFences(text: String): String {
        var trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed

        trimmed = trimmed.removePrefix("```json").removePrefix("```").trim()
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }
        return trimmed
    }

    fun extractBalancedJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    fun extractWidgetJson(raw: String): String? {
        val stripped = stripMarkdownFences(raw)
        return extractBalancedJsonObject(stripped)
    }
}
