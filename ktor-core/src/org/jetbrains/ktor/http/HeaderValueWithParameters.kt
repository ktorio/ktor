package org.jetbrains.ktor.http

abstract class HeaderValueWithParameters(protected val content: String, val parameters: List<HeaderValueParam> = emptyList()) {

    fun parameter(name: String) = parameters.firstOrNull { it.name == name }?.value

    override fun toString(): String = when {
        parameters.isEmpty() -> content
        else -> parameters.joinToString("; ", prefix = "$content; ") { "${it.name}=${it.value.escapeIfNeeded()}" }
    }

    companion object {
        fun <R> parse(value: String, init: (String, List<HeaderValueParam>) -> R) = parseHeaderValue(value).single().let { preParsed ->
            init(preParsed.value, preParsed.params)
        }
    }
}

private fun String.escapeIfNeeded() = when {
    indexOfAny("\"=;,\\/".toCharArray()) != -1 -> quote()
    else -> this
}

private fun String.quote() = "\"" + replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\""
