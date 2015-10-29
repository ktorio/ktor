package org.jetbrains.ktor.util

import kotlin.text.*

fun String.substringAfterMatch(mr: MatchResult) = drop(mr.range.end + if (mr.range.isEmpty()) 0 else 1)

fun String.unescapeIfQuoted() = when {
    startsWith('"') && endsWith('"') -> {
        val escapeRegex = "\\\\.".toRegex()
        removeSurrounding("\"").replace(escapeRegex) { it.value.takeLast(1) }
    }
    else -> this
}

