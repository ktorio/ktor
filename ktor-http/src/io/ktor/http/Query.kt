package io.ktor.http

import io.ktor.util.*

fun parseQueryString(query: String, limit: Int = 1000): ValuesMap {
    return if (query.isBlank()) {
        ValuesMap.Empty
    } else {
        ValuesMap.build { parse(query, limit) }
    }
}

private fun ValuesMapBuilder.parse(query: String, limit: Int) {
    var count = 0
    var startIndex = 0
    var equalIndex = -1
    for (index in query.indices) {
        if (count == limit)
            return
        val ch = query[index]
        when (ch) {
            '&' -> {
                appendParam(query, startIndex, equalIndex, index)
                startIndex = index + 1
                equalIndex = -1
                count++
            }
            '=' -> {
                if (equalIndex == -1)
                    equalIndex = index
            }
        }
    }
    if (count == limit)
        return
    appendParam(query, startIndex, equalIndex, query.length)
}

private fun ValuesMapBuilder.appendParam(query: String, nameIndex: Int, equalIndex: Int, endIndex: Int) {
    if (equalIndex == -1) {
        val name = decodeURLQueryComponent(query, nameIndex, endIndex).trim()
        append(name, "")
    } else {
        val name = decodeURLQueryComponent(query, nameIndex, equalIndex).trim()
        val value = decodeURLQueryComponent(query, equalIndex + 1, endIndex).trim()
        append(name, value)
    }
}
