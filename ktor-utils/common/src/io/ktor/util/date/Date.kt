/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import io.ktor.util.*

/**
 * According to:
 * http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/time.h.html
 */

/**
 * Day of week
 * @property shortName is 3 letter shortcut
 * @property fullName is a full capitalized name
 */
@Suppress("KDocMissingDocumentation")
enum class WeekDay(val shortName: String) {
    MONDAY("Mon"),
    TUESDAY("Tue"),
    WEDNESDAY("Wed"),
    THURSDAY("Thu"),
    FRIDAY("Fri"),
    SATURDAY("Sat"),
    SUNDAY("Sun");

    @Deprecated("Use shortName instead.", ReplaceWith("shortName"))
    val value: String
        get() = shortName

    val fullName: String = name.toLowerCase().capitalize()

    companion object {
        /**
         * Lookup an instance by [ordinal]
         */
        fun from(ordinal: Int): WeekDay = values()[ordinal]

        /**
         * Lookup an instance by short week day name [WeekDay.shortName]
         */
        @Deprecated(
            "Use fromShortName instead.",
            ReplaceWith("WeekDay.fromShortName(value)", "io.ktor.util.date.WeekDay")
        )
        fun from(value: String): WeekDay = fromShortName(value)

        /**
         * Lookup an instance by short week day name [WeekDay.shortName]
         */
        fun fromShortName(shortName: String): WeekDay = values().find { it.shortName == shortName }
            ?: error("Invalid day of week: $shortName")

        internal val MinNameLength: Int = values().minBy { it.name.length }!!.name.length

        internal val MaxNameLength: Int = values().maxBy { it.name.length }!!.name.length
    }
}

/**
 * Month
 * @property shortName is 3 letter shortcut
 * @property fullName is a full capitalized name
 */
@Suppress("KDocMissingDocumentation")
enum class Month(val shortName: String) {
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

    @Deprecated("Use shortName instead.", ReplaceWith("shortName"))
    val value: String
        get() = shortName

    val fullName: String = name.toLowerCase().capitalize()

    companion object {
        /**
         * Lookup an instance by [ordinal]
         */
        fun from(ordinal: Int): Month = values()[ordinal]

        /**
         * Lookup an instance by short month name [Month.shortName]
         */
        @Deprecated(
            "Use fromShortName instead.",
            ReplaceWith("Month.fromShortName(value)", "io.ktor.util.date.Month")
        )
        fun from(value: String): Month = fromShortName(value)

        /**
         * Lookup an instance by short month name [Month.shortName]
         */
        fun fromShortName(value: String): Month = values().find { it.shortName == value }
            ?: error("Invalid month short name: $value")

        internal val MinNameLength: Int = values().minBy { it.name.length }!!.name.length
        internal val MaxNameLength: Int = values().maxBy { it.name.length }!!.name.length
    }
}

/**
 * Date in GMT timezone
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
data class GMTDate internal constructor(
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

    companion object {
        /**
         * An instance of [GMTDate] corresponding to the epoch beginning
         */
        val START: GMTDate = GMTDate(0)
    }
}

/**
 * Create new gmt date from the [timestamp].
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
@Suppress("FunctionName")
expect fun GMTDate(timestamp: Long? = null): GMTDate

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
@Suppress("FunctionName")
expect fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate

/**
 * Adds the specified number of [milliseconds]
 */
operator fun GMTDate.plus(milliseconds: Long): GMTDate = GMTDate(timestamp + milliseconds)

/**
 * Subtracts the specified number of [milliseconds]
 */
operator fun GMTDate.minus(milliseconds: Long): GMTDate = GMTDate(timestamp - milliseconds)

/**
 * Truncate to seconds by discarding sub-second part
 */
fun GMTDate.truncateToSeconds(): GMTDate = GMTDate(seconds, minutes, hours, dayOfMonth, month, year)
