package org.jetbrains.ktor.http

import org.jetbrains.ktor.util.*

public fun parseQueryString(query: String): ValuesMap {
    return if (query.isBlank()) {
        ValuesMap.Empty
    } else {
        ValuesMap.build {
            val parameterSegments = query.splitToSequence("&").filter { it.isNotBlank() }
            for (segment in parameterSegments) {
                val pair = segment.split('=', limit = 2)
                val name = pair[0].decodeURL().trim()
                when (pair.size) {
                    1 -> append(name, "")
                    2 -> {
                        val value = pair[1].decodeURL().trim()
                        append(name, value)
                    }
                }
            }
        }
    }
}