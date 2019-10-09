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

    val hasRemaining: Boolean get() = index < source.length

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

internal fun Char.isDelimiter(): Boolean =
    this == '\u0009'
        || this in ('\u0020'..'\u002f')
        || this in ('\u003b'..'\u0040')
        || this in ('\u005b'..'\u0060')
        || this in ('\u007b'..'\u007e')

internal fun Char.isNonDelimiter(): Boolean =
    this in ('\u0000'..'\u0008')
        || this in ('\u000a'..'\u001f')
        || this in ('0'..'9')
        || this == ':'
        || this in ('a'..'z')
        || this in ('A'..'Z')
        || this in ('\u007f'..'\u00ff')

internal fun Char.isOctet(): Boolean =
    this in ('\u0000'..'\u00ff')

internal fun Char.isNonDigit(): Boolean =
    this in ('\u0000'..'\u002f') || this in ('\u004a'..'\u00ff')

internal inline fun Boolean.otherwise(block: () -> Unit) {
    if (!this) block()
}

internal fun CookieDateBuilder.handleToken(token: String) {

}

class CookieDateParser {

    fun parse(source: String): GMTDate {
        val lexer = StringLexer(source)
        val builder = CookieDateBuilder()

        lexer.acceptWhile { it.isDelimiter() }

        while (lexer.hasRemaining) {
            if (lexer.test { it.isNonDelimiter() }) {
                val token = lexer.capture { acceptWhile { it.isNonDelimiter() } }

                builder.handleToken(token)

                lexer.acceptWhile { it.isDelimiter() }
            }
        }

        when (builder.year) {
            in 70..99 -> builder.year = builder.year!! + 1900
            in 0..69 -> builder.year = builder.year!! + 2000
        }

        if (builder.dayOfMonth == null)
            throw InvalidDateStringException(source, 0, "Could not find day-of-month")

        if (builder.month == null)
            throw InvalidDateStringException(source, 0, "Could not find month")

        if (builder.year == null)
            throw InvalidDateStringException(source, 0, "Could not find year")

        if (builder.hours == null || builder.minutes == null || builder.seconds == null)
            throw InvalidDateStringException(source, 0, "Could not find time")

        if (builder.dayOfMonth!! !in 1..31)
            throw InvalidDateStringException(source, 0, "Invalid day of month: ${builder.dayOfMonth} not in [1,31]")

        if (builder.year!! < 1601)
            throw InvalidDateStringException(source, 0, "Invalid year: ${builder.year} < 1601")

        if (builder.hours!! > 23)
            throw InvalidDateStringException(source, 0, "Invalid hours: ${builder.hours} > 23")

        if (builder.minutes!! > 59)
            throw InvalidDateStringException(source, 0, "Invalid minutes: ${builder.minutes} > 59")

        if (builder.seconds!! > 59)
            throw InvalidDateStringException(source, 0, "Invalid seconds: ${builder.seconds} > 59")

        return builder.build()
    }
}

internal class CookieDateBuilder {
    var seconds: Int? = null
    var minutes: Int? = null
    var hours: Int? = null

    var dayOfMonth: Int? = null
    var month: Month? = null
    var year: Int? = null

    fun build(): GMTDate = GMTDate(seconds!!, minutes!!, hours!!, dayOfMonth!!, month!!, year!!)
}
