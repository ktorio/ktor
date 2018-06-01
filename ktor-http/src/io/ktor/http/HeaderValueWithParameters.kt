package io.ktor.http

import io.ktor.compat.*

abstract class HeaderValueWithParameters(protected val content: String, val parameters: List<HeaderValueParam> = emptyList()) {

    fun parameter(name: String) = parameters.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    override fun toString(): String = when {
        parameters.isEmpty() -> content
        else -> {
            val size = content.length + parameters.sumBy { it.name.length + it.value.length + 3 }
            StringBuilder(size).apply {
                append(content)
                for ((name, value) in parameters) {
                    append("; ")
                    append(name)
                    append("=")
                    value.escapeIfNeededTo(this)
                }
            }.toString()
        }
    }

    companion object {
        inline fun <R> parse(value: String, init: (String, List<HeaderValueParam>) -> R): R {
            val headerValue = parseHeaderValue(value).single()
            return init(headerValue.value, headerValue.params)
        }
    }
}

private val CHARACTERS_SHOULD_BE_ESCAPED = "\"=;,\\/ ".toCharArray()
fun String.escapeIfNeeded() = when {
    indexOfAny(CHARACTERS_SHOULD_BE_ESCAPED) != -1 -> quote()
    else -> this
}

private fun String.escapeIfNeededTo(out: StringBuilder) {
    when {
        indexOfAny(CHARACTERS_SHOULD_BE_ESCAPED) != -1 -> quoteTo(out)
        else -> out.append(this)
    }
}

fun String.quote() = buildString { this@quote.quoteTo(this) }
private fun String.quoteTo(out: StringBuilder) {
    out.append("\"")
    for (i in 0 until length) {
        val ch = this[i]
        when (ch) {
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\"' -> out.append("\\\"")
            else -> out.append(ch)
        }
    }
    out.append("\"")
}
