/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.date.*

/**
 * Lexer over a source string, allowing testing, accepting and capture of spans of characters given
 * some predicates.
 *
 * @property source string to iterate over
 */
internal class StringLexer(val source: String) {
    var index = 0

    /**
     * Check if the current character satisfies the predicate
     *
     * @param predicate character test
     */
    fun test(predicate: (Char) -> Boolean): Boolean =
        index < source.length && predicate(source[index])

    /**
     * Checks if the current character satisfies the predicate, consuming it is so
     *
     * @param predicate character test
     */
    fun accept(predicate: (Char) -> Boolean): Boolean =
        test(predicate).also { if (it) index++ }

    /**
     * Keep accepting characters while they satisfy the predicate
     *
     * @param predicate character test
     * @see [accept]
     */
    fun acceptWhile(predicate: (Char) -> Boolean): Boolean {
        if (!test(predicate)) return false
        while (test(predicate)) index++
        return true
    }

    /**
     * Run the block on this lexer taking note of the starting and ending index. Returning the span of the
     * source which was traversed.
     *
     * @param block scope of traversal to be captured
     * @return The traversed span of the source string
     */
    inline fun capture(block: StringLexer.() -> Unit): String {
        val start = index
        block()
        return source.substring(start, index)
    }
}

class CookieDateParser {

    fun parse(source: String): GMTDate = TODO()
}
