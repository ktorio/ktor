/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.datetime.*

// impl based on kotlix.datetime

@Suppress("FunctionName")
internal fun GMTDateImpl(timestamp: Long?): GMTDate {
    val instant = timestamp?.let(Instant::fromEpochMilliseconds) ?: Clock.System.now()
    val localDate = instant.toLocalDateTime(TimeZone.UTC)
    return GMTDate(
        seconds = localDate.second,
        minutes = localDate.minute,
        hours = localDate.hour,
        dayOfWeek = WeekDay.from(localDate.dayOfWeek.ordinal),
        dayOfMonth = localDate.dayOfMonth,
        dayOfYear = localDate.dayOfYear,
        month = Month.from(localDate.monthNumber - 1),
        year = localDate.year,
        timestamp = instant.toEpochMilliseconds()
    )
}

@Suppress("FunctionName")
internal fun GMTDateImpl(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate {
    return GMTDateImpl(
        LocalDateTime(
            year = year,
            monthNumber = month.ordinal + 1,
            dayOfMonth = dayOfMonth,
            hour = hours,
            minute = minutes,
            second = seconds
        ).toInstant(TimeZone.UTC).toEpochMilliseconds()
    )
}
