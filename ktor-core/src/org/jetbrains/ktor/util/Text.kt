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

internal inline fun String.chomp(separator: String, onMissingDelimiter: () -> Pair<String, String>): Pair<String, String> {
    val idx = indexOf(separator)
    return when (idx) {
        -1 -> onMissingDelimiter()
        else -> substring(0, idx) to substring(idx + 1)
    }
}

