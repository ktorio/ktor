/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import java.util.*

private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86400L
private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_DAY = SECONDS_PER_DAY * MILLIS_PER_SECOND

private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private val CUMULATIVE_DAYS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0)

private fun daysInMonth(month: Int, year: Int): Int =
    if (month == 1 && isLeapYear(year)) 29 else DAYS_IN_MONTH[month]

/**
 * Create new gmt date from the [timestamp].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 *
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
@Suppress("FunctionName")
public actual fun GMTDate(timestamp: Long?): GMTDate {
    val ts = timestamp ?: System.currentTimeMillis()

    val totalSeconds = ts / MILLIS_PER_SECOND
    val secondOfDay = ((totalSeconds % SECONDS_PER_DAY) + SECONDS_PER_DAY).toInt() % SECONDS_PER_DAY.toInt()

    val hours = secondOfDay / SECONDS_PER_HOUR
    val minutes = (secondOfDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = secondOfDay % SECONDS_PER_MINUTE

    var daysSinceEpoch = (ts / MILLIS_PER_DAY).toInt()
    if (ts < 0 && ts % MILLIS_PER_DAY != 0L) daysSinceEpoch--

    val dayOfWeek = WeekDay.from(((daysSinceEpoch % 7) + 7 + 3) % 7)

    var year = 1970
    var remainingDays = daysSinceEpoch

    while (remainingDays >= 365) {
        val daysInYear = if (isLeapYear(year)) 366 else 365
        if (remainingDays < daysInYear) break
        remainingDays -= daysInYear
        year++
    }
    while (remainingDays < 0) {
        year--
        remainingDays += if (isLeapYear(year)) 366 else 365
    }

    val dayOfYear = remainingDays + 1

    var monthValue = 0
    var day = remainingDays
    while (monthValue < 11 && day >= daysInMonth(monthValue, year)) {
        day -= daysInMonth(monthValue, year)
        monthValue++
    }

    return GMTDate(
        seconds = seconds,
        minutes = minutes,
        hours = hours,
        dayOfWeek = dayOfWeek,
        dayOfMonth = day + 1,
        dayOfYear = dayOfYear,
        month = Month.from(monthValue),
        year = year,
        timestamp = ts
    )
}

/**
 * Create an instance of [GMTDate] from the specified date/time components
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 */
@Suppress("FunctionName")
public actual fun GMTDate(
    seconds: Int,
    minutes: Int,
    hours: Int,
    dayOfMonth: Int,
    month: Month,
    year: Int
): GMTDate {
    val monthValue = month.ordinal
    val dayOfYear = CUMULATIVE_DAYS[monthValue] + dayOfMonth +
        if (monthValue > 1 && isLeapYear(year)) 1 else 0

    var daysSinceEpoch = 0
    for (y in 1970 until year) {
        daysSinceEpoch += if (isLeapYear(y)) 366 else 365
    }
    for (y in year until 1970) {
        daysSinceEpoch -= if (isLeapYear(y)) 366 else 365
    }
    daysSinceEpoch += dayOfYear - 1

    val dayOfWeek = WeekDay.from(((daysSinceEpoch % 7) + 7 + 3) % 7)

    val timestamp = daysSinceEpoch.toLong() * MILLIS_PER_DAY +
        hours * SECONDS_PER_HOUR * MILLIS_PER_SECOND +
        minutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND +
        seconds * MILLIS_PER_SECOND

    return GMTDate(
        seconds = seconds,
        minutes = minutes,
        hours = hours,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        dayOfYear = dayOfYear,
        month = month,
        year = year,
        timestamp = timestamp
    )
}

public fun Calendar.toDate(timestamp: Long?): GMTDate {
    timestamp?.let { timeInMillis = it }
    val timeZoneOffset = get(Calendar.ZONE_OFFSET) + get(Calendar.DST_OFFSET)

    val seconds = get(Calendar.SECOND)
    val minutes = get(Calendar.MINUTE)
    val hours = get(Calendar.HOUR_OF_DAY)

    /**
     * from (SUN 1) (MON 2) .. (SAT 7) to (SUN 6) (MON 0) .. (SAT 5)
     */
    val numberOfDay = (get(Calendar.DAY_OF_WEEK) + 7 - 2) % 7
    val dayOfWeek = WeekDay.from(numberOfDay)

    val dayOfMonth = get(Calendar.DAY_OF_MONTH)
    val dayOfYear = get(Calendar.DAY_OF_YEAR)

    val month = Month.from(get(Calendar.MONTH))
    val year = get(Calendar.YEAR)

    return GMTDate(
        seconds, minutes, hours,
        dayOfWeek, dayOfMonth, dayOfYear,
        month, year,
        timeInMillis + timeZoneOffset
    )
}

/**
 * Convert to [Date]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.toJvmDate)
 */
public fun GMTDate.toJvmDate(): Date = Date(timestamp)

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.getTimeMillis)
 */
public actual fun getTimeMillis(): Long = System.currentTimeMillis()
