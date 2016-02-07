package org.jetbrains.ktor.http

import org.jetbrains.ktor.util.*

fun parseQueryString(query: String): ValuesMap {
    return if (query.isBlank()) {
        ValuesMap.Empty
    } else {
        ValuesMap.build {
            val parameterSegments = query.splitToSequence("&").filter { it.isNotBlank() }
            for (segment in parameterSegments) {
                val pair = segment.split('=', limit = 2)
                val name = decodeURLQueryComponent(pair[0]).trim()
                when (pair.size) {
                    1 -> append(name, "")
                    2 -> {
                        val value = decodeURLQueryComponent(pair[1]).trim()
                        append(name, value)
                    }
                }
            }
        }
    }
}