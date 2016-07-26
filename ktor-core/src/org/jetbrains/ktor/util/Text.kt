package org.jetbrains.ktor.util

import kotlin.text.*

fun String.substringAfterMatch(mr: MatchResult) = drop(mr.range.endInclusive + if (mr.range.isEmpty()) 0 else 1)

private val escapeRegex = "\\\\.".toRegex()
fun String.unescapeIfQuoted() = when {
    startsWith('"') && endsWith('"') -> removeSurrounding("\"").replace(escapeRegex) { it.value.takeLast(1) }
    else -> this
}

fun String.tryParseFloat(): Float {
    try {
        return toFloat()
    } catch(e: NumberFormatException) {
        return 0f
    }
}

fun String.tryParseDouble(): Double {
    try {
        return toDouble()
    } catch(e: NumberFormatException) {
        return 0.0
    }
}

fun String.escapeHTML(): String {
    if (isEmpty()) {
        return this
    }

    val sb = StringBuilder(length)

    for (idx in 0 .. length - 1) {
        val ch = this[idx]

        when (ch) {
            '\'' -> sb.append("&apos;")
            '\"' -> sb.append("&quot")
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            else -> sb.append(ch)
        }
    }

    return sb.toString()
}

internal inline fun String.chomp(separator: String, onMissingDelimiter: () -> Pair<String, String>): Pair<String, String> {
    val idx = indexOf(separator)
    return when (idx) {
        -1 -> onMissingDelimiter()
        else -> substring(0, idx) to substring(idx + 1)
    }
}

