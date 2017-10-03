package org.jetbrains.ktor.http

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
                    append(value.escapeIfNeeded())
                }
            }.toString()
        }
    }

    companion object {
        fun <R> parse(value: String, init: (String, List<HeaderValueParam>) -> R) = parseHeaderValue(value).single().let { preParsed ->
            init(preParsed.value, preParsed.params)
        }
    }
}

fun String.escapeIfNeeded() = when {
    indexOfAny("\"=;,\\/".toCharArray()) != -1 -> quote()
    else -> this
}

fun String.quote() = "\"" + replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\""
