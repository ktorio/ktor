/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing.regex

import io.ktor.http.parsing.*

internal fun Grammar.buildRegexParser(): Parser {
    val groups = mutableMapOf<String, MutableList<Int>>()
    val expression = toRegex(groups).regex

    return RegexParser(Regex(expression), groups)
}

private class GrammarRegex(
    regexRaw: String,
    groupsCountRaw: Int = 0,
    group: Boolean = false
) {
    val regex = if (group) "($regexRaw)" else regexRaw
    val groupsCount = if (group) groupsCountRaw + 1 else groupsCountRaw
}

private fun Grammar.toRegex(
    groups: MutableMap<String, MutableList<Int>>,
    offset: Int = 1,
    shouldGroup: Boolean = false
): GrammarRegex = when (this) {
    is StringGrammar -> GrammarRegex(Regex.escape(value))
    is RawGrammar -> GrammarRegex(value)
    is NamedGrammar -> {
        val nested = grammar.toRegex(groups, offset + 1)
        groups.add(name, offset)
        GrammarRegex(nested.regex, nested.groupsCount, group = true)
    }
    is ComplexGrammar -> {
        val expression = StringBuilder()

        var currentOffset = if (shouldGroup) offset + 1 else offset
        grammars.forEachIndexed { index, grammar ->
            val current = grammar.toRegex(groups, currentOffset, shouldGroup = true)

            if (index != 0 && this is OrGrammar) expression.append("|")
            expression.append(current.regex)
            currentOffset += current.groupsCount
        }

        val groupsCount = if (shouldGroup) currentOffset - offset - 1 else currentOffset - offset
        GrammarRegex(expression.toString(), groupsCount, shouldGroup)
    }
    is SimpleGrammar -> {
        val operator = when (this) {
            is MaybeGrammar -> '?'
            is ManyGrammar -> '*'
            is AtLeastOne -> '+'
            else -> error("Unsupported simple grammar element: $this")
        }

        val nested = grammar.toRegex(groups, offset, shouldGroup = true)
        GrammarRegex("${nested.regex}$operator", nested.groupsCount)
    }
    is AnyOfGrammar -> GrammarRegex("[${Regex.escape(value)}]")
    is RangeGrammar -> GrammarRegex("[$from-$to]")
    else -> error("Unsupported grammar element: $this")
}

private fun MutableMap<String, MutableList<Int>>.add(key: String, value: Int) {
    if (!contains(key)) this[key] = mutableListOf()
    this[key]!! += value
}
