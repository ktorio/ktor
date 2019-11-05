/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import io.ktor.util.date.*
import io.ktor.util.logging.*
import kotlin.math.*

/**
 * Adds event time to logger in the specified [dateFormat] measuring time using the specified [clock].
 * @param clock to measure time
 * @param dateFormat in which dates will be formatted
 */
fun LoggingConfigBuilder.logTime(
    clock: () -> GMTDate = { GMTDate() },
    dateFormat: Appendable.(GMTDate) -> Unit = Appendable::appendLogEventTime
) {
    val key = CurrentDateKey()
    registerKey(key)

    enrich {
        this[key] = clock()
    }
    label { event ->
        dateFormat(event[key])
    }
}

/**
 * Adds event time with no formatted label
 */
internal fun LoggingConfigBuilder.ensureLogTime(clock: () -> GMTDate = { GMTDate() }) {
    val key = CurrentDateKey()
    registerKey(key)

    enrich {
        this[key] = clock()
    }
}

/**
 * Returns log record's log time or `null` if [logTime] feature is not installed.
 */
val LogRecord.logTime: GMTDate?
    get() = config.keys.lastOrNull { it is CurrentDateKey }?.let { get(it as CurrentDateKey) }

private class CurrentDateKey : LogAttributeKey<GMTDate>("time", GMTDate())

/**
 * Default logging date format
 */
fun Appendable.appendLogEventTime(date: GMTDate) {
    append(date.dayOfWeek.value)
    append(", ")
    append2(date.dayOfMonth)
    append(' ')
    append(date.month.value)
    append(' ')
    append4(date.year)
    append(' ')
    append2(date.hours)
    append(':')
    append2(date.minutes)
    append(':')
    append2(date.seconds)
    append('.')

    append3((date.timestamp % 1000L).toInt())
}

private fun Appendable.append2(number: Int) {
    if (number < 10) {
        append('0')
    }
    append(number.toString())
}

private fun Appendable.append4(number: Int) {
    if (number < 1000) {
        return appendNFallback(number, 4)
    }
    append(number.toString())
}

private fun Appendable.append3(number: Int) {
    if (number < 100) {
        return appendNFallback(number, 3)
    }
    append(number.toString())
}

private val zeroes = arrayOf("0", "00", "000", "0000")

private fun Appendable.appendNFallback(number: Int, count: Int) {
    if (number == 0) {
        append(zeroes[count - 1])
        return
    }

    val padSize = count - log10(number.toDouble()).toInt() - 2

    append(zeroes[padSize])
    append(number.toString())
}
