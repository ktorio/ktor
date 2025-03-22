/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.serialization.*
import kotlin.time.*

// According to: http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/time.h.html

/**
 * Day of week
 * [value] is 3 letter shortcut
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.WeekDay)
 */
public enum class WeekDay(public val value: String) {
    MONDAY("Mon"),
    TUESDAY("Tue"),
    WEDNESDAY("Wed"),
    THURSDAY("Thu"),
    FRIDAY("Fri"),
    SATURDAY("Sat"),
    SUNDAY("Sun");

    public companion object {
        /**
         * Lookup an instance by [ordinal]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.WeekDay.Companion.from)
         */
        public fun from(ordinal: Int): WeekDay = entries[ordinal]

        /**
         * Lookup an instance by short week day name [WeekDay.value]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.WeekDay.Companion.from)
         */
        public fun from(value: String): WeekDay = entries.find { it.value == value }
            ?: error("Invalid day of week: $value")
    }
}

/**
 * Month
 * [value] is 3 letter shortcut
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.Month)
 */
public enum class Month(public val value: String) {
    JANUARY("Jan"),
    FEBRUARY("Feb"),
    MARCH("Mar"),
    APRIL("Apr"),
    MAY("May"),
    JUNE("Jun"),
    JULY("Jul"),
    AUGUST("Aug"),
    SEPTEMBER("Sep"),
    OCTOBER("Oct"),
    NOVEMBER("Nov"),
    DECEMBER("Dec");

    public companion object {
        /**
         * Lookup an instance by [ordinal]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.Month.Companion.from)
         */
        public fun from(ordinal: Int): Month = entries[ordinal]

        /**
         * Lookup an instance by short month name [Month.value]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.Month.Companion.from)
         */
        public fun from(value: String): Month = entries.find { it.value == value }
            ?: error("Invalid month: $value")
    }
}

/**
 * Date in GMT timezone
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 *
 * @property seconds: seconds from 0 to 60(last is for leap second)
 * @property minutes: minutes from 0 to 59
 * @property hours: hours from 0 to 23
 * @property dayOfWeek an instance of the corresponding day of week
 * @property dayOfMonth: day of month from 1 to 31
 * @property dayOfYear: day of year from 1 to 366
 * @property month an instance of the corresponding month
 * @property year: year in common era(CE: https://en.wikipedia.org/wiki/Common_Era)
 *
 * @property timestamp is a number of epoch milliseconds
 */
@Serializable
public data class GMTDate(
    val seconds: Int,
    val minutes: Int,
    val hours: Int,

    val dayOfWeek: WeekDay,
    val dayOfMonth: Int,
    val dayOfYear: Int,

    val month: Month,
    val year: Int,

    val timestamp: Long
) : Comparable<GMTDate> {

    override fun compareTo(other: GMTDate): Int = timestamp.compareTo(other.timestamp)

    public fun copy(): GMTDate = GMTDate()

    public companion object {
        /**
         * An instance of [GMTDate] corresponding to the epoch beginning
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate.Companion.START)
         */
        public val START: GMTDate = GMTDate(0)
    }
}

/**
 * Create new gmt date from the [timestamp].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 *
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
public expect fun GMTDate(timestamp: Long? = null): GMTDate

/**
 * Create an instance of [GMTDate] from the specified date/time components
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 */
public expect fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate

/**
 * Adds the specified number of [milliseconds]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.plus)
 */
public operator fun GMTDate.plus(milliseconds: Long): GMTDate = GMTDate(timestamp + milliseconds)

/**
 * Subtracts the specified number of [milliseconds]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.minus)
 */
public operator fun GMTDate.minus(milliseconds: Long): GMTDate = GMTDate(timestamp - milliseconds)

/**
 * Adds the specified [duration]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.plus)
 */
public operator fun GMTDate.plus(duration: Duration): GMTDate = GMTDate(timestamp + duration.inWholeMilliseconds)

/**
 * Subtracts the specified [duration]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.minus)
 */
public operator fun GMTDate.minus(duration: Duration): GMTDate = GMTDate(timestamp - duration.inWholeMilliseconds)

/**
 * Truncate to seconds by discarding sub-second part
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.truncateToSeconds)
 */
public fun GMTDate.truncateToSeconds(): GMTDate = GMTDate(seconds, minutes, hours, dayOfMonth, month, year)

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.getTimeMillis)
 */
public expect fun getTimeMillis(): Long
