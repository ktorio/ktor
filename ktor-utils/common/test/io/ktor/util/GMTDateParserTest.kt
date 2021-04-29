/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.date.*
import kotlin.test.*

class GMTDateParserTest {
    @Test
    fun testFormats() {
        val formats = arrayOf(
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
            val parser = GMTDateParser(formats[index])
            val (dateString, expected) = dates[index]
            val date = parser.parse(dateString)

            assertEquals(expected, date)
        }
    }
}
