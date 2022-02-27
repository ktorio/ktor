/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Create new gmt date from the [timestamp].
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
@OptIn(UnsafeNumber::class)
public actual fun GMTDate(timestamp: Long?): GMTDate = memScoped {
    val timeHolder = alloc<time_tVar>()
    val current: Long = if (timestamp == null) {
        time(timeHolder.ptr)
        timeHolder.value * 1000L
    } else {
        timeHolder.value = (timestamp / 1000).convert()
        timestamp
    }

    val dateInfo = alloc<tm>()
    gmtime_r(timeHolder.ptr, dateInfo.ptr)

    with(dateInfo) {
        // from (SUN, 0) to (SUN, 6)
        val weekDay = (tm_wday + 7 - 1) % 7
        val year = tm_year + 1900

        GMTDate(
            tm_sec, tm_min, tm_hour,
            WeekDay.from(weekDay), tm_mday, tm_yday,
            Month.from(tm_mon), year, current
        )
    }
}

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
public actual fun GMTDate(
    seconds: Int,
    minutes: Int,
    hours: Int,
    dayOfMonth: Int,
    month: Month,
    year: Int
): GMTDate = memScoped {
    val nativeYear = year - 1900

    val dateInfo = alloc<tm>().apply {
        tm_sec = seconds
        tm_min = minutes
        tm_hour = hours

        tm_mday = dayOfMonth

        tm_mon = month.ordinal
        tm_year = nativeYear

        tm_isdst = 0
    }

    val timestamp = system_time(dateInfo.ptr)

    return GMTDate(timestamp * 1000L)
}

@Suppress("FunctionName")
internal expect fun system_time(tm: CValuesRef<tm>?): Long

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 */
public actual fun getTimeMillis(): Long = GMTDate().timestamp
