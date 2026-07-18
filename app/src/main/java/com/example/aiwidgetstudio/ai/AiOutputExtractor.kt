package com.example.aiwidgetstudio.ai

object AiOutputExtractor {

    fun extractWidgetJson(raw: String): String? {
        val stripped = stripMarkdownFences(raw)
        return extractBalancedJsonObject(stripped)
    }

    private fun stripMarkdownFences(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) t = t.removePrefix("```json").removePrefix("```").trim()
        if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        return t
    }

    private fun extractBalancedJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                escaped = if (escaped) false else c == '\\'
                if (!escaped && c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
}
