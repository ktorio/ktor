/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import java.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")

/**
 * Create new gmt date with the [durationSinceEpoch].
 */
@Suppress("FunctionName")
public actual fun GMTDate(durationSinceEpoch: Duration): GMTDate {
    val calendar = Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!
    calendar.timeInMillis = durationSinceEpoch.inWholeMilliseconds
    return calendar.toDate(durationSinceEpoch)
}

/**
 * Create new gmt current date.
 */
@Suppress("FunctionName")
internal actual fun GMTDate(): GMTDate =
    Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!.toDate(null)

/**
 * Create an instance of [GMTDate] from the specified date/time components
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
    val calendar = Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)!!
    calendar.apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month.ordinal)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, hours)
        set(Calendar.MINUTE, minutes)
        set(Calendar.SECOND, seconds)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.toDate(durationSinceEpoch = null)
}

private fun Calendar.toDate(durationSinceEpoch: Duration?): GMTDate {
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
        durationSinceEpoch ?: time.time.milliseconds
    )
}

/**
 * Convert to [Date]
 */
public fun GMTDate.toJvmDate(): Date = Date(durationSinceEpoch.inWholeMilliseconds)
