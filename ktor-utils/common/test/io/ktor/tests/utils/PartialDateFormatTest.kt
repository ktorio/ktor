/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.date.*
import kotlin.test.*

class PartialDateFormatTest {
    @Test
    fun testDate() {
        test("2019-01-02", date(year = 2019, month = Month.JANUARY, dayOfMonth = 2), "yyyy-MM-dd")
        test("2019-01-2", date(year = 2019, month = Month.JANUARY, dayOfMonth = 2), "yyyy-MM-d")
        test("2019-Jan-2", date(year = 2019, month = Month.JANUARY, dayOfMonth = 2), "yyyy-MMM-d")
        test("19-Jan-2", date(year = 2019, month = Month.JANUARY, dayOfMonth = 2), "yy-MMM-d")
        test("19-Feb-2", date(year = 2019, month = Month.FEBRUARY, dayOfMonth = 2), "yy-MMM-d")
        test("19-February-2", date(year = 2019, month = Month.FEBRUARY, dayOfMonth = 2), "yy-MMMM-d")
        test("19-February-12", date(year = 2019, month = Month.FEBRUARY, dayOfMonth = 12), "yy-MMMM-d")
    }

    @Test
    fun testTime() {
        test("01:02:03", date(hours = 1, minutes = 2, seconds = 3), "HH:mm:ss")
        test("11:22:33", date(hours = 11, minutes = 22, seconds = 33), "HH:mm:ss")
        test("1:2:3", date(hours = 1, minutes = 2, seconds = 3), "H:m:s")
        test("11:22:33", date(hours = 11, minutes = 22, seconds = 33), "H:m:s")
    }

    @Test
    fun testZones() {
        test("GMT", date(), "z")
    }

    @Test
    fun testDayOfWeek() {
        test("Tue", date(year = 2019, month = Month.NOVEMBER, dayOfMonth = 12), "EEE")
        test("Tuesday", date(year = 2019, month = Month.NOVEMBER, dayOfMonth = 12), "EEEE")
    }

    private fun test(expected: String, date: GMTDate, format: String) {
        val pattern = StringPatternDateFormat(format)
        val formatted = date.format(pattern)
        assertEquals(expected, formatted)
    }

    private fun date(
        seconds: Int = 0,
        minutes: Int = 0,
        hours: Int = 0,
        dayOfMonth: Int = 1,
        month: Month = Month.JANUARY,
        year: Int = 2019
    ): GMTDate {
        return GMTDate(seconds, minutes, hours, dayOfMonth, month, year)
    }
}
