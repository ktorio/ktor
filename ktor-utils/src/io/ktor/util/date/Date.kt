package io.ktor.util.date

import kotlin.math.*

/**
 * According to:
 * http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/time.h.html
 */

/**
 * Day of week
 * [value] is 3 letter shortcut
 */
enum class WeekDay(val value: String) {
    MONDAY("Mon"),
    TUESDAY("Tue"),
    WEDNESDAY("Wed"),
    THURSDAY("Thu"),
    FRIDAY("Fri"),
    SATURDAY("Sat"),
    SUNDAY("Sun");

    companion object {
        fun from(ordinal: Int): WeekDay = WeekDay.values()[ordinal]

        fun from(value: String): WeekDay = WeekDay.values().find { it.value == value }
            ?: error("Invalid day of week: $value")
    }
}

/**
 * Month
 * [value] is 3 letter shortcut
 */
enum class Month(val value: String) {
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

    companion object {
        fun from(ordinal: Int): Month = Month.values()[ordinal]

        fun from(value: String): Month = Month.values().find { it.value == value }
            ?: error("Invalid month: $value")
    }
}

/**
 * Date in GMT timezone
 *
 * [seconds]: seconds from 0 to 60(last is for leap second)
 * [minutes]: minutes from 0 to 59
 * [hours]: hours from 0 to 23
 *
 * [dayOfMonth]: day of month from 1 to 31
 * [dayOfYear]: day of year from 1 to 366
 *
 * [year]: year in common era(CE: https://en.wikipedia.org/wiki/Common_Era)
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
) {

    companion object {
        val START = GMTDate(0)
    }
}

/**
 * Create new gmt date from the [timestamp].
 * The [timestamp] is `now` by default.
 */
expect fun GMTDate(timestamp: Long? = null): GMTDate

expect fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate

operator fun GMTDate.compareTo(other: GMTDate): Int = (timestamp - other.timestamp).sign
operator fun GMTDate.plus(millis: Long): GMTDate = GMTDate(timestamp + millis)
operator fun GMTDate.minus(millis: Long): GMTDate = GMTDate(timestamp - millis)
