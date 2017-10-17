package io.ktor.http.request

import io.ktor.http.*
import io.ktor.util.*

fun parseQueryString(query: String, limit: Int = 1000): ValuesMap {
    return if (query.isBlank()) {
        ValuesMap.Empty
    } else {
        ValuesMap.build {
            val parameterSegments = query.splitToSequence("&", limit = limit).filter { it.isNotBlank() }
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
