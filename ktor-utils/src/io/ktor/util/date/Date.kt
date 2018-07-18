package io.ktor.util.date

import kotlin.math.*

/**
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
    }
}

/**
 * Month
 * [value] is 3 letter shortcut]
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
    }
}

/**
 * Date in GMT timezone
 */
data class GMTDate(
    val seconds: Int,
    val minutes: Int,
    val hours: Int,

    val dayOfWeek: WeekDay,
    val dayOfMonth: Int,
    val dayOfYear: Int,

    val month: Month,
    val year: Int,

    val timestamp: Long
)

/**
 * Create new gmt date from the [timestamp].
 * The [timestamp] is `now` by default.
 */
expect fun GMTDate(timestamp: Long? = null): GMTDate

expect fun GMTDate(
    seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int
): GMTDate

operator fun GMTDate.compareTo(other: GMTDate): Int = (timestamp - other.timestamp).sign
operator fun GMTDate.plus(millis: Long): GMTDate = GMTDate(timestamp + millis)
operator fun GMTDate.minus(millis: Long): GMTDate = GMTDate(timestamp - millis)
