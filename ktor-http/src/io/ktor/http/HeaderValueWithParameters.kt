package io.ktor.http

import io.ktor.util.*

/**
 * Represents a header value that consist of [content] followed by [parameters].
 * Useful for headers such as `Content-Type`, `Content-Disposition` and so on.
 *
 * @property content header's content without parameters
 * @property parameters
 */
abstract class HeaderValueWithParameters(
    protected val content: String,
    val parameters: List<HeaderValueParam> = emptyList()
) {

    /**
     * The first value for the parameter with [name] comparing case-insensitively or `null` if no such parameters found
     */
    fun parameter(name: String): String? = parameters.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    override fun toString(): String = when {
        parameters.isEmpty() -> content
        else -> {
            val size = content.length + parameters.sumBy { it.name.length + it.value.length + 3 }
            StringBuilder(size).apply {
                append(content)
                for (index in 0 until parameters.size) {
                    val (name, value) = parameters[index]
                    append("; ")
                    append(name)
                    append("=")
                    value.escapeIfNeededTo(this)
                }
            }.toString()
        }
    }

    companion object {
        /**
         * Parse header with parameter and pass it to [init] function to instantiate particular type
         */
        inline fun <R> parse(value: String, init: (String, List<HeaderValueParam>) -> R): R {
            val headerValue = parseHeaderValue(value).single()
            return init(headerValue.value, headerValue.params)
        }
    }
}

/**
 * Append formatted header value to the builder
 */
fun StringValuesBuilder.append(name: String, value: HeaderValueWithParameters) {
    append(name, value.toString())
}

/**
 * Escape using double quotes if needed or keep as is if no dangerous strings found
 */
@InternalAPI
fun String.escapeIfNeeded() = when {
    checkNeedEscape() -> quote()
    else -> this
}

@Suppress("NOTHING_TO_INLINE")
private inline fun String.escapeIfNeededTo(out: StringBuilder) {
    when {
        checkNeedEscape() -> out.append(this.quote())
        else -> out.append(this)
    }
}

private fun String.checkNeedEscape(): Boolean {
    if (isEmpty()) return true

    for (index in 0 until length) {
        when (this[index]) {
            '\\',
            '\n',
            '\r',
            '\"',
            ' ',
            '=',
            ';',
            ',',
            '/' -> return true
        }
    }

    return false
}

/**
 * Escape string using double quotes
 */
@InternalAPI
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
