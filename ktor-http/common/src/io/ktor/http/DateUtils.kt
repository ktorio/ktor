/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.date.*
import io.ktor.util.date.Month
import kotlinx.datetime.*
import kotlin.native.concurrent.*

@SharedImmutable
private val HTTP_DATE_FORMATS = listOf(
    "***, dd MMM YYYY hh:mm:ss zzz",
    "****, dd-MMM-YYYY hh:mm:ss zzz",
    "*** MMM d hh:mm:ss YYYY",
    "***, dd-MMM-YYYY hh:mm:ss zzz",
    "***, dd-MMM-YYYY hh-mm-ss zzz",
    "***, dd MMM YYYY hh:mm:ss zzz",
    "*** dd-MMM-YYYY hh:mm:ss zzz",
    "*** dd MMM YYYY hh:mm:ss zzz",
    "*** dd-MMM-YYYY hh-mm-ss zzz",
    "***,dd-MMM-YYYY hh:mm:ss zzz",
    "*** MMM d YYYY hh:mm:ss zzz"
)

/**
 * Convert valid http date [String] to [Instant] trying various http date formats from [HTTP_DATE_FORMATS]
 *
 * Note that only GMT(UTC) date is valid http date.
 */
public fun String.fromHttpToGmtDate(): Instant = with(trim()) {
    for (format in HTTP_DATE_FORMATS) {
        try {
            val parser = GMTDateParser(format)
            return parser.parse(this@fromHttpToGmtDate)
        } catch (_: InvalidDateStringException) {
        }
    }

    error("Failed to parse date: $this")
}

/**
 * Convert valid cookie date [String] to [Instant] trying first the RFC6265 standard, falling back on [fromHttpToGmtDate]
 *
 * @see [fromHttpToGmtDate]
 */
public fun String.fromCookieToGmtDate(): Instant = with(trim()) {
    try {
        val parser = CookieDateParser()
        return parser.parse(this@with)
    } catch (_: InvalidCookieDateException) {
    }

    return fromHttpToGmtDate()
}

private fun LocalDateTime.toHttpDate(): String = buildString {
    append("${dayOfWeek.toWeekDay().value}, ")
    append("${dayOfMonth.padZero(2)} ")
    append("${month.toMonth().value} ")
    append(year.padZero(4))
    append(" ${hour.padZero(2)}:${minute.padZero(2)}:${second.padZero(2)} ")
    append("GMT")
}

/**
 * Convert [Instant] to valid http date [String]
 */
public fun Instant.toHttpDate(): String = toLocalDateTime(TimeZone.UTC).toHttpDate()

private fun Int.padZero(length: Int): String = toString().padStart(length, '0')

private fun kotlinx.datetime.DayOfWeek.toWeekDay(): WeekDay = WeekDay.from(ordinal)
private fun kotlinx.datetime.Month.toMonth(): Month = Month.from(ordinal)
