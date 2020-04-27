/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.test.*

class CookieDateParserTest {

    @Test
    fun testHttpGmtFormats() {

        val eleven = GMTDate(1, 45, 12, 11, Month.APRIL, 2018)
        val first = GMTDate(1, 45, 12, 1, Month.APRIL, 2018)

        val dates = listOf(
            "Wed, 11 Apr 2018 12:45:01 GMT" to eleven,
            "Wedn, 11-Apr-2018 12:45:01 GMT" to eleven,
            "Wed Apr 1 12:45:01 2018" to first,
            "Wed, 11-Apr-2018 12:45:01 GMT" to eleven,
            "Wed, 11-Apr-2018 12-45-01 GMT" to eleven,
            "Wed, 11 Apr 2018 12:45:01 GMT" to eleven,
            "Wed 11-Apr-2018 12:45:01 GMT" to eleven,
            "Wed 11 Apr 2018 12:45:01 GMT" to eleven,
            "Wed 11-Apr-2018 12-45-01 GMT" to eleven,
            "Wed,11-Apr-2018 12:45:01 GMT" to eleven,
            "Wed Apr 1 2018 12:45:01 GMT" to first
        )

        for (index in dates.indices) {
            val (dateString, expected) = dates[index]
            val date = dateString.fromHttpToGmtDate()

            assertEquals(expected, date)
        }
    }

    @Test
    fun testRfc6265Format() {
        val one = GMTDate(1, 1, 1, 1, Month.APRIL, 2018)
        val eleventh = GMTDate(1, 45, 12, 11, Month.APRIL, 2018)

        val dates = listOf(
            // Time field is placement-independent
            "12:45:01 11 Apr 2018" to eleventh,
            "11 12:45:01 Apr 2018" to eleventh,
            "11 Apr 12:45:01 2018" to eleventh,
            "11 Apr 2018 12:45:01" to eleventh,

            // Day-of-month field is placement-independent before year
            "11 Apr 2018 12:45:01" to eleventh,
            "Apr 11 2018 12:45:01" to eleventh,
            "Apr 12:45:01 11 2018" to eleventh,

            // Month is placement-independent
            "Apr 11 2018 12:45:01" to eleventh,
            "11 Apr 2018 12:45:01" to eleventh,
            "11 2018 Apr 12:45:01" to eleventh,
            "11 2018 12:45:01 Apr" to eleventh,

            // Year is placement-independent after day-of-month
            "Apr 11 2018 12:45:01" to eleventh,
            "Apr 11 12:45:01 2018" to eleventh,

            // Day-of-month, hours, minutes and seconds can be 1 digit long,
            // Year can be 2 digits long
            "1 Apr 18 1:1:1" to one
        )

        for ((date, expected) in dates) {
            val parser = CookieDateParser()
            assertEquals(expected, parser.parse(date))
        }
    }

    @Test
    fun testBoundaryChecks() {
        val incorrect = listOf(
            "Wed, 0 Apr 2018 12:45:01", // Day of month < 1
            "Wed, 32 Apr 2018 12:45:01", // Day of month > 31
            "Wed, 1 Apr 1600 12:45:01", // Year < 1601
            "Wed, 32 Apr 2018 24:45:01", // Hour > 24
            "Wed, 32 Apr 2018 12:60:01", // Minute > 59
            "Wed, 32 Apr 2018 12:45:60" // Second > 59
        )

        for (date in incorrect) {
            val parser = CookieDateParser()
            assertFailsWith(InvalidCookieDateException::class) {
                parser.parse(date)
            }
        }
    }

    @Test
    fun testCorrectlyMutatesYear() {
        val tests = mapOf(
            // 70 <= year <= 99, increment by 1900
            "Wed, 11 Apr 70 12:45:01 GMT" to GMTDate(1, 45, 12, 11, Month.APRIL, 1970),
            "Wed, 11 Apr 99 12:45:01 GMT" to GMTDate(1, 45, 12, 11, Month.APRIL, 1999),

            // 0 <= year <= 69, increment by 2000
            "Wed, 11 Apr 00 12:45:01 GMT" to GMTDate(1, 45, 12, 11, Month.APRIL, 2000),
            "Wed, 11 Apr 69 12:45:01 GMT" to GMTDate(1, 45, 12, 11, Month.APRIL, 2069)
        )

        for ((date, expected) in tests) {
            val parser = CookieDateParser()
            assertEquals(expected, parser.parse(date))
        }
    }
}
