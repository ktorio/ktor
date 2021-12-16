/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlin.js.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

internal actual fun GMTDate(): GMTDate = with(Date()) {
    toGMTDate(getTime().milliseconds)
}

public actual fun GMTDate(durationSinceEpoch: Duration): GMTDate {
    return Date(durationSinceEpoch.toLong(DurationUnit.MILLISECONDS)).toGMTDate(durationSinceEpoch)
}

private fun Date.toGMTDate(inputDuration: Duration): GMTDate {
    if (getTime().isNaN()) throw InvalidTimestampException(inputDuration)
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

        inputDuration
    )
}

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
public actual fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate {
    val durationSinceEpoch = Date.UTC(year, month.ordinal, dayOfMonth, hours, minutes, seconds).milliseconds
    return GMTDate(durationSinceEpoch)
}

/**
 * Invalid exception: possible overflow or underflow
 */
public class InvalidTimestampException(epochDuration: Duration) : IllegalStateException(
    "Invalid date timestamp exception: $epochDuration"
)
