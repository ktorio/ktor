/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing.regex

import io.ktor.http.parsing.*

internal class RegexParser(
    private val expression: Regex,
    private val indexes: Map<String, List<Int>>
) : Parser {
    override fun parse(input: String): ParseResult? {
        val match = expression.matchEntire(input)
        if (match == null || match.value.length != input.length) {
            return null
        }

        val mapping = mutableMapOf<String, List<String>>()
        indexes.forEach { (key, locations) ->
            locations.forEach { index ->
                val result = mutableListOf<String>()
                match.groups[index]?.let { result += it.value }
                if (result.isNotEmpty()) mapping[key] = result
            }
        }

        return ParseResult(mapping)
    }

    override fun match(input: String): Boolean = expression matches input
}
