/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.date

import io.ktor.util.*
import io.ktor.util.date.GMTClock.*
import kotlin.time.*

/**
 * According to:
 * http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/time.h.html
 */

/**
 * Day of week
 * [value] is 3 letter shortcut
 */
@Suppress("KDocMissingDocumentation")
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
         */
        public fun from(ordinal: Int): WeekDay = values()[ordinal]

        /**
         * Lookup an instance by short week day name [WeekDay.value]
         */
        public fun from(value: String): WeekDay = values().find { it.value == value }
            ?: error("Invalid day of week: $value")
    }
}

/**
 * Month
 * [value] is 3 letter shortcut
 */
@Suppress("KDocMissingDocumentation")
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
         */
        public fun from(ordinal: Int): Month = values()[ordinal]

        /**
         * Lookup an instance by short month name [Month.value]
         */
        public fun from(value: String): Month = values().find { it.value == value }
            ?: error("Invalid month: $value")
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
 * @property durationSinceEpoch is the duration since epoch
 */
public class GMTDate internal constructor(
    public val seconds: Int,
    public val minutes: Int,
    public val hours: Int,

    public val dayOfWeek: WeekDay,
    public val dayOfMonth: Int,
    public val dayOfYear: Int,

    public val month: Month,
    public val year: Int,

    public val durationSinceEpoch: Duration
) : Comparable<GMTDate> {

    override fun compareTo(other: GMTDate): Int = durationSinceEpoch.compareTo(other.durationSinceEpoch)

    public companion object {
        /**
         * An instance of [GMTDate] corresponding to the epoch beginning
         */
        public val START: GMTDate = GMTDate(Duration.ZERO)
    }

    /**
     * Adds the specified [duration]
     */
    public operator fun plus(duration: Duration): GMTDate = GMTDate(durationSinceEpoch + duration)

    /**
     * Subtracts the specified [duration]
     */
    public operator fun minus(duration: Duration): GMTDate = GMTDate(durationSinceEpoch - duration)

    /**
     * Returns the [time difference][Duration] between [this] and [other]
     */
    public operator fun minus(other: GMTDate): Duration = durationSinceEpoch - other.durationSinceEpoch

    // Just use durationSinceEpoch and no "human" readable properties
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GMTDate

        if (durationSinceEpoch != other.durationSinceEpoch) return false

        return true
    }

    override fun hashCode(): Int {
        return durationSinceEpoch.hashCode()
    }

    override fun toString(): String {
        return "GMTDate(year=$year, month=$month, dayOfMonth=$dayOfMonth, hours=$hours, minutes=$minutes, " +
            "seconds=$seconds, dayOfWeek=$dayOfWeek, dayOfYear=$dayOfYear)"
    }
}

/**
 * Create new gmt date from the [durationSinceEpoch].
 */
@Suppress("FunctionName")
public expect fun GMTDate(durationSinceEpoch: Duration): GMTDate

/**
 * Create new gmt date from the current system time.
 */
@Suppress("FunctionName")
@Deprecated("Internal usage only, cannot be private due to expect", level = DeprecationLevel.ERROR)
@PublishedApi
internal expect fun GMTDate(): GMTDate

/**
 * Create an instance of [GMTDate] from the specified date/time components
 */
@Suppress("FunctionName")
public expect fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate

/**
 * Truncate to seconds by discarding sub-second part
 */
public fun GMTDate.truncateToSeconds(): GMTDate = GMTDate(seconds, minutes, hours, dayOfMonth, month, year)

/**
 * Provides the current GMTDate. Use [System] in production code, which is used by default in:
 * [Server][io.ktor.server.application.ApplicationEnvironment.clock]
 * [Client][io.ktor.client.engine.HttpClientEngineConfig.clock]
 */
public interface GMTClock {
    public fun now(): GMTDate

    /**
     * Returns the current GMTDate based on the system clock. This clock supports NTP updates.
     */
    public companion object System : GMTClock {
        @Suppress("DEPRECATION_ERROR")
        override fun now(): GMTDate = GMTDate()
    }
}

/**
 * Creates a testable [GMTClock] from a [TimeSource].
 *
 * THIS IMPLEMENTATION SHOULD NEVER BE CALLED IN PRODUCTION CODE.
 *
 * A [TimeSource] has no contracts regarding precision and monotonic, so it could be updated due to NTP sync,
 * which results into wrong [GMTDate]s.
 * In production, use [GMTClock.System]!
 *
 * This clock stores the initial [TimeMark], so repeatedly creating [GMTDate]s from the same [TimeSource] results
 * into different [GMTDate]s iff the time of the [TimeSource] was increased. To sync different [GMTClock]s, use the [offset]
 * parameter.
 *
 * Only use it in tests:
 * ```kt
 * @Test
 * fun testing() {
 *    val testTimeSource = TestTimeSource()
 *    val clock = testTimeSource.toGMTClock()
 *
 *    val first = clock.now()
 *    testTimeSource += 10.seconds
 *    val second = clock.now()
 * }
 * ```
 */
@ExperimentalTime
public fun TimeSource.toGMTClock(offset: GMTDate = GMTDate(Duration.ZERO)): GMTClock = object : GMTClock {
    private val epoch = markNow()

    override fun now() = offset + epoch.elapsedNow()
}
