/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import io.ktor.util.*

private typealias DateChunkParser = GMTDateBuilder.(String) -> Unit

/**
 * Build [GMTDate] parser using [pattern] string.
 *
 * Pattern string format:
 * | Unit     | pattern char | Description                                          |
 * | -------- | ------------ | ---------------------------------------------------- |
 * | Seconds  | s            | parse seconds 0 to 60                                |
 * | Minutes  | m            | parse minutes 0 to 60                                |
 * | Hours    | h            | parse hours 0 to 23                                  |
 * | Month    | M            | parse month from Jan to Dec(see [Month] for details) |
 * | Year     | Y            | parse year                                           |
 * | Any char | *            | Match any character                                  |
 */
@InternalAPI
class GMTDateParser(private val pattern: String) {
    init {
        check(pattern.isNotEmpty()) { "Date parser pattern shouldn't be empty." }
    }

    /**
     * Parse [GMTDate] from [dateString] using [pattern].
     */
    fun parse(dateString: String): GMTDate {
        val builder = GMTDateBuilder()

        var start = 0
        var current = pattern[start]
        var chunkStart = 0
        var index = 1

        try {
            while (index < pattern.length) {
                if (pattern[index] == current) {
                    index++
                    continue
                }

                val chunkEnd = chunkStart + index - start
                builder.handleToken(current, dateString.substring(chunkStart, chunkEnd))

                chunkStart = chunkEnd
                start = index
                current = pattern[index]

                index++
            }

            if (chunkStart < dateString.length) {
                builder.handleToken(current, dateString.substring(chunkStart))
            }
        } catch (_: Throwable) {
            throw InvalidDateStringException(dateString, chunkStart, pattern)
        }

        return builder.build()
    }

    private fun GMTDateBuilder.handleToken(
        type: Char, chunk: String
    ): Unit = when (type) {
        SECONDS -> {
            seconds = chunk.toInt()
        }
        MINUTES -> {
            minutes = chunk.toInt()
        }
        HOURS -> {
            hours = chunk.toInt()
        }
        DAY_OF_MONTH -> {
            dayOfMonth = chunk.toInt()
        }
        MONTH -> {
            month = Month.fromShortName(chunk)
        }
        YEAR -> {
            year = chunk.toInt()
        }
        ZONE ->
            check(chunk == "GMT")
        ANY -> Unit
        else -> {
            check(chunk.all { it == type })
        }
    }

    companion object {
        const val SECONDS = 's'
        const val MINUTES = 'm'
        const val HOURS = 'h'

        const val DAY_OF_MONTH = 'd'
        const val MONTH = 'M'
        const val YEAR = 'Y'

        const val ZONE = 'z'

        const val ANY = '*'
    }

}

internal class GMTDateBuilder {
    var seconds: Int? = null
    var minutes: Int? = null
    var hours: Int? = null

    var dayOfMonth: Int? = null
    lateinit var month: Month
    var year: Int? = null

    fun build(): GMTDate = GMTDate(seconds!!, minutes!!, hours!!, dayOfMonth!!, month, year!!)
}

/**
 * Thrown when the date string doesn't the string pattern.
 */
class InvalidDateStringException(
    data: String, at: Int, pattern: String
) : IllegalStateException("Failed to parse date string: \"${data}\" at index $at. Pattern: \"$pattern\"")
