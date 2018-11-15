package io.ktor.util.date

import kotlin.js.*

actual fun GMTDate(timestamp: Long?): GMTDate {
    val date = timestamp?.toDouble()?.let { Date(it) } ?: Date()

    if (date.getTime().isNaN()) throw InvalidTimestampException(timestamp!!)

    with(date) {
        /* from SUNDAY 0 -> MONDAY 0 */
        val dayOfWeek = WeekDay.from((getUTCDay() + 6) % 7)

        val month = Month.from(getUTCMonth())

        return GMTDate(
            getUTCSeconds(),
            getUTCMinutes(),
            getUTCHours(),

            dayOfWeek,
            getUTCDate(),
            getUTCFullYear(),

            month,
            getUTCFullYear(),

            getTime().toLong()
        )
    }
}

actual fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate {
    val timestamp = Date.UTC(year, month.ordinal, dayOfMonth, hours, minutes, seconds).toLong()
    return GMTDate(timestamp)
}

/**
 * Invalid exception: possible overflow or underflow
 */
class InvalidTimestampException(timestamp: Long) : IllegalStateException(
    "Invalid date timestamp exception: $timestamp"
)
