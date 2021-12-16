/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

internal actual fun GMTDate(): GMTDate = memScoped {
    val timeHolder = alloc<time_tVar>()
    time(timeHolder.ptr)
    val current = timeHolder.value.seconds
    GMTDate(current)
}

public actual fun GMTDate(durationSinceEpoch: Duration): GMTDate = memScoped {
    val timeHolder = alloc<time_tVar>()
    timeHolder.value = durationSinceEpoch.inWholeSeconds.convert()

    val dateInfo = alloc<tm>()
    gmtime_r(timeHolder.ptr, dateInfo.ptr)

    with(dateInfo) {
        // from (SUN, 0) to (SUN, 6)
        val weekDay = (tm_wday + 7 - 1) % 7
        val year = tm_year + 1900

        GMTDate(
            tm_sec, tm_min, tm_hour,
            WeekDay.from(weekDay), tm_mday, tm_yday,
            Month.from(tm_mon), year, durationSinceEpoch
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

    return GMTDate(timestamp.seconds)
}

@Suppress("FunctionName")
internal expect fun system_time(tm: CValuesRef<tm>?): Long
