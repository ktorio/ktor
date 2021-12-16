/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.date

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
