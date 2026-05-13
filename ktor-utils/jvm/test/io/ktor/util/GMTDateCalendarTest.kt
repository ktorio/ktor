/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.date.*
import java.util.*
import kotlin.test.*

class GMTDateCalendarTest {

    @Test
    fun testTimestampIsConsistent() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        val actual = calendar.toDate(null)
        val expected = GMTDate(actual.timestamp)

        assertEquals(expected, actual)
    }

    @Test
    fun testTimestampIsDifferentWithDifferentTimezone() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        val gmt = GMTDate()
        val actual = calendar.toDate(null)

        val offset: Long = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)).toLong()
        val diff = gmt.timestamp + offset - actual.timestamp
        assertTrue(diff >= 0)
        assertTrue(diff < 1000)
    }

    @Test
    fun `epoch timestamp is 1970-01-01 00_00_00 GMT`() {
        val date = GMTDate(0L)
        assertEquals(1970, date.year)
        assertEquals(Month.JANUARY, date.month)
        assertEquals(1, date.dayOfMonth)
        assertEquals(0, date.hours)
        assertEquals(0, date.minutes)
        assertEquals(0, date.seconds)
        assertEquals(WeekDay.THURSDAY, date.dayOfWeek)
        assertEquals(1, date.dayOfYear)
    }

    @Test
    fun `leap year 2000 Feb 29 is handled correctly`() {
        // Feb 29, 2000 12:00:00 GMT - leap year divisible by 400
        val timestamp = 951825600000L
        val date = GMTDate(timestamp)
        assertEquals(2000, date.year)
        assertEquals(Month.FEBRUARY, date.month)
        assertEquals(29, date.dayOfMonth)
        assertEquals(WeekDay.TUESDAY, date.dayOfWeek)
        assertEquals(60, date.dayOfYear)
    }

    @Test
    fun `1900 is not a leap year`() {
        // March 1, 1900 00:00:00 GMT - not a leap year (divisible by 100 but not 400)
        val timestamp = -2203891200000L
        val date = GMTDate(timestamp)
        assertEquals(1900, date.year)
        assertEquals(Month.MARCH, date.month)
        assertEquals(1, date.dayOfMonth)
        assertEquals(60, date.dayOfYear)
    }

    @Test
    fun `leap year 2024 Feb 29 with time components`() {
        // Feb 29, 2024 15:30:45 GMT
        val timestamp = 1709220645000L
        val date = GMTDate(timestamp)
        assertEquals(2024, date.year)
        assertEquals(Month.FEBRUARY, date.month)
        assertEquals(29, date.dayOfMonth)
        assertEquals(15, date.hours)
        assertEquals(30, date.minutes)
        assertEquals(45, date.seconds)
        assertEquals(WeekDay.THURSDAY, date.dayOfWeek)
        assertEquals(60, date.dayOfYear)
    }

    @Test
    fun `year boundary between 2023 and 2024`() {
        // Dec 31, 2023 23:59:59 GMT
        val timestamp = 1704067199000L
        val date = GMTDate(timestamp)
        assertEquals(2023, date.year)
        assertEquals(Month.DECEMBER, date.month)
        assertEquals(31, date.dayOfMonth)
        assertEquals(23, date.hours)
        assertEquals(59, date.minutes)
        assertEquals(59, date.seconds)
        assertEquals(365, date.dayOfYear)

        // Jan 1, 2024 00:00:00 GMT
        val timestamp2 = 1704067200000L
        val date2 = GMTDate(timestamp2)
        assertEquals(2024, date2.year)
        assertEquals(Month.JANUARY, date2.month)
        assertEquals(1, date2.dayOfMonth)
        assertEquals(0, date2.hours)
        assertEquals(0, date2.minutes)
        assertEquals(0, date2.seconds)
        assertEquals(1, date2.dayOfYear)
    }

    @Test
    fun `all month boundaries in 2023`() {
        // Verify each month boundary in 2023 (non-leap year)
        val monthTimestamps = listOf(
            1672531200000L to Pair(Month.JANUARY, 1), // Jan 1
            1675209600000L to Pair(Month.FEBRUARY, 1), // Feb 1
            1677628800000L to Pair(Month.MARCH, 1), // Mar 1
            1680307200000L to Pair(Month.APRIL, 1), // Apr 1
            1682899200000L to Pair(Month.MAY, 1), // May 1
            1685577600000L to Pair(Month.JUNE, 1), // Jun 1
            1688169600000L to Pair(Month.JULY, 1), // Jul 1
            1690848000000L to Pair(Month.AUGUST, 1), // Aug 1
            1693526400000L to Pair(Month.SEPTEMBER, 1), // Sep 1
            1696118400000L to Pair(Month.OCTOBER, 1), // Oct 1
            1698796800000L to Pair(Month.NOVEMBER, 1), // Nov 1
            1701388800000L to Pair(Month.DECEMBER, 1), // Dec 1
        )

        for ((timestamp, expected) in monthTimestamps) {
            val date = GMTDate(timestamp)
            assertEquals(2023, date.year, "Year mismatch for $expected")
            assertEquals(expected.first, date.month, "Month mismatch for $expected")
            assertEquals(expected.second, date.dayOfMonth, "Day mismatch for $expected")
        }
    }

    @Test
    fun `day of week progresses correctly from epoch`() {
        // Starting from epoch (Thursday), verify day of week progression
        val thursdayTimestamp = 0L
        val fridayTimestamp = 86400000L
        val saturdayTimestamp = 172800000L
        val sundayTimestamp = 259200000L
        val mondayTimestamp = 345600000L
        val tuesdayTimestamp = 432000000L
        val wednesdayTimestamp = 518400000L

        assertEquals(WeekDay.THURSDAY, GMTDate(thursdayTimestamp).dayOfWeek)
        assertEquals(WeekDay.FRIDAY, GMTDate(fridayTimestamp).dayOfWeek)
        assertEquals(WeekDay.SATURDAY, GMTDate(saturdayTimestamp).dayOfWeek)
        assertEquals(WeekDay.SUNDAY, GMTDate(sundayTimestamp).dayOfWeek)
        assertEquals(WeekDay.MONDAY, GMTDate(mondayTimestamp).dayOfWeek)
        assertEquals(WeekDay.TUESDAY, GMTDate(tuesdayTimestamp).dayOfWeek)
        assertEquals(WeekDay.WEDNESDAY, GMTDate(wednesdayTimestamp).dayOfWeek)
    }

    @Test
    fun `matches Calendar implementation for various timestamps`() {
        // Verify our implementation matches Calendar for various timestamps
        val gmtTimezone = TimeZone.getTimeZone("GMT")
        val testTimestamps = listOf(
            0L, // Epoch
            System.currentTimeMillis(), // Now
            1704067200000L, // Jan 1, 2024
            951825600000L, // Feb 29, 2000
            -86400000L, // Dec 31, 1969
            -1L, // Dec 31, 1969 23:59:59.999
            -999L, // Dec 31, 1969 23:59:59.001
            253402300799000L, // Dec 31, 9999 23:59:59
        )

        for (timestamp in testTimestamps) {
            val calendar = Calendar.getInstance(gmtTimezone, Locale.ROOT).apply {
                timeInMillis = timestamp
            }

            val date = GMTDate(timestamp)

            assertEquals(
                calendar.get(Calendar.YEAR),
                date.year,
                "Year mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.MONTH),
                date.month.ordinal,
                "Month mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.DAY_OF_MONTH),
                date.dayOfMonth,
                "Day of month mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.HOUR_OF_DAY),
                date.hours,
                "Hours mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.MINUTE),
                date.minutes,
                "Minutes mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.SECOND),
                date.seconds,
                "Seconds mismatch for timestamp $timestamp"
            )
            assertEquals(
                calendar.get(Calendar.DAY_OF_YEAR),
                date.dayOfYear,
                "Day of year mismatch for timestamp $timestamp"
            )
        }
    }
}
