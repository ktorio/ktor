/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JVM implementation of GMTDate factory functions.
// ABOUTME: Uses zero-allocation timestamp-to-date computation for the common case.

package io.ktor.util.date

import java.util.*

private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")

// Days in each month for non-leap and leap years
private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private val DAYS_IN_MONTH_LEAP = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

// Cumulative days before each month (0-indexed) for non-leap and leap years
private val DAYS_BEFORE_MONTH = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
private val DAYS_BEFORE_MONTH_LEAP = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)

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
    return timestampToGMTDate(ts)
}

/**
 * Computes GMTDate from epoch milliseconds without any object allocation.
 * Uses direct arithmetic instead of Calendar to avoid GregorianCalendar allocation.
 */
private fun timestampToGMTDate(timestamp: Long): GMTDate {
    // Total seconds since epoch (floor division for negative timestamps)
    val totalSeconds = Math.floorDiv(timestamp, 1000L)

    // Time of day
    val secondOfDay = Math.floorMod(totalSeconds, 86400L)
    val seconds = (secondOfDay % 60).toInt()
    val minutes = ((secondOfDay / 60) % 60).toInt()
    val hours = (secondOfDay / 3600).toInt()

    // Days since epoch (Jan 1, 1970 was a Thursday)
    val days = Math.floorDiv(totalSeconds, 86400L).toInt()

    // Day of week: Jan 1, 1970 was Thursday (index 3 in our enum where Monday=0)
    val dayOfWeek = WeekDay.from(((days % 7) + 3 + 7) % 7)

    // Compute year, month, day using a standard algorithm
    // Shift to March 1, year 0 epoch for easier leap year handling
    var z = days + 719468 // Days since March 1, year 0

    // Handle negative values
    val era: Int
    val dayOfEra: Int
    if (z >= 0) {
        era = z / 146097
        dayOfEra = z % 146097
    } else {
        era = (z - 146096) / 146097
        dayOfEra = z - era * 146097
    }

    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear400 = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val mp = (5 * dayOfYear400 + 2) / 153
    val dayOfMonth = dayOfYear400 - (153 * mp + 2) / 5 + 1
    val monthIndex = if (mp < 10) mp + 2 else mp - 10
    val finalYear = if (monthIndex <= 1) year + 1 else year

    val isLeap = isLeapYear(finalYear)
    val daysBeforeMonth = if (isLeap) DAYS_BEFORE_MONTH_LEAP else DAYS_BEFORE_MONTH
    val dayOfYear = daysBeforeMonth[monthIndex] + dayOfMonth

    return GMTDate(
        seconds = seconds,
        minutes = minutes,
        hours = hours,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        dayOfYear = dayOfYear,
        month = Month.from(monthIndex),
        year = finalYear,
        timestamp = timestamp
    )
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0)

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
): GMTDate = (Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!).apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month.ordinal)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
    set(Calendar.HOUR_OF_DAY, hours)
    set(Calendar.MINUTE, minutes)
    set(Calendar.SECOND, seconds)
    set(Calendar.MILLISECOND, 0)
}.toDate(timestamp = null)

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
