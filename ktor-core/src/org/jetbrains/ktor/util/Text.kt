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

