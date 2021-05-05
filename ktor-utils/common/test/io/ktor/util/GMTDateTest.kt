/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.date.*
import kotlin.test.*
import kotlin.time.*

class GMTDateTest {

    @Test
    fun testConstruction() {
        val first = GMTDate()
        val second = GMTDate(timestamp = first.timestamp)

        val third = GMTDate(
            first.seconds,
            first.minutes,
            first.hours,
            first.dayOfMonth,
            first.month,
            first.year
        )

        assertEquals(first, second)
        assertEquals(first.timestamp / 1000, third.timestamp / 1000)
    }

    @Test
    fun testComparison() {
        val before = GMTDate(1L)
        val after = GMTDate(3L)
        val inTheMiddle = GMTDate(2L)

        assertTrue { before < after }
        assertTrue { inTheMiddle in before..after }

        val farDate = GMTDate(after.timestamp * 1000)

        assertTrue { farDate > before }
        assertTrue { farDate > after }
        assertTrue { before < farDate }
        assertTrue { farDate == farDate }
        assertEquals(0, farDate.compareTo(farDate))
    }

    @ExperimentalTime
    @Test
    fun testDurationArithmetic() {
        val now = GMTDate()
        val plus10Secs = now + 10.seconds
        assertTrue { now < plus10Secs }
        assertEquals(now.plus(10_000), plus10Secs)
        assertEquals(now, plus10Secs - 10.seconds)
    }
}
