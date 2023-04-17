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
}
