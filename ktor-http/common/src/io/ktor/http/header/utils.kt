/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.header

internal fun splitHeaderValues(values: List<String>, separator: Char, splitInsideQuotes: Boolean): List<String> {
    return values.flatMap { splitHeaderValue(it, separator, splitInsideQuotes) }
}

/**
 * Splits a header [value] by the given [separator] with support for [splitInsideQuotes].
 *
 * If [splitInsideQuotes] is true, the function ignores quotes entirely and splits everywhere the [separator] appears.
 * Otherwise, [separator] that occurs inside a double-quoted string isn't treated as a split point.
 *
 * Rules:
 * - Supports backslash escaping inside quotes (e.g., `\"`).
 * - Trims optional whitespace around items.
 * - Skips empty items produced by consecutive separators.
 * - Preserves original quoting/escaping.
 *
 *  @param value The raw header value string.
 *  @param separator The character on which to split (e.g., ',' or ';').
 *  @param splitInsideQuotes If `true`, quotes are ignored and splitting occurs at every [separator].
 *                           If `false`, separators inside quoted strings are not considered split points.
 */
private fun splitHeaderValue(value: String, separator: Char, splitInsideQuotes: Boolean): List<String> {
    if (splitInsideQuotes) {
        return value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
    }

    val result = mutableListOf<String>()
    var start = 0
    var i = 0
    var inQuotes = false
    var escape = false

    fun emit(start: Int, end: Int) {
        val token = value.substring(start, end).trim()
        if (token.isNotEmpty()) result.add(token)
    }

    while (i < value.length) {
        val ch = value[i]
        if (inQuotes) {
            when {
                escape -> escape = false
                ch == '\\' -> escape = true
                ch == '"' -> inQuotes = false
            }
            i++
            continue
        }

        when (ch) {
            '"' -> {
                inQuotes = true
                i++
            }

            separator -> {
                emit(start, i)
                i++
                start = i
            }

            else -> i++
        }
    }

    emit(start, value.length)
    return result
}
