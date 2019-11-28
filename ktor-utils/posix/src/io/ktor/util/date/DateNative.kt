/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import platform.posix.*
import utils.*

actual fun GMTDate(timestamp: Long?): GMTDate = memScoped {
    val timeHolder = alloc<time_tVar>()
    val current = timestamp ?: now()
    timeHolder.value = (current / 1000).convert()

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

actual fun GMTDate(
    seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int
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

    val timestamp = ktor_time(dateInfo.ptr)

    return GMTDate(timestamp * 1000L)
}

private fun MemScope.now(): Long {
    val time = alloc<timeval>()
    if (gettimeofday(time.ptr, null) == -1) {
        throw PosixException.forErrno()
    }
    return time.tv_sec.convert<Long>() * 1000L +
        time.tv_usec.convert<Long>() / 1000L
}
