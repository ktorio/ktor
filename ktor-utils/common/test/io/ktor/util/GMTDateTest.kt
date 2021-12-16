/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.date.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@ExperimentalTime
class GMTDateTest {

    @Test
    fun testConstruction() {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()
        testTimeSource += 10.seconds

        val first = clock.now()
        assertEquals(10.seconds, first.durationSinceEpoch)
        clock.test()
    }

    private fun GMTClock.test() {
        val first = now()

        val second = GMTDate(first.durationSinceEpoch)

        val third = GMTDate(
            first.seconds,
            first.minutes,
            first.hours,
            first.dayOfMonth,
            first.month,
            first.year
        )

        assertEquals(first, second)
        assertEquals(0, (third - first).inWholeSeconds)
    }

    @Test
    fun systemClock() {
        val systemClock = GMTClock.System
        systemClock.test()
    }

    @Test
    fun testComparison() {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        val before = clock.now()
        testTimeSource += 10.seconds
        val inTheMiddle = clock.now()
        testTimeSource += 10.seconds
        val after = clock.now()

        assertTrue { before < after }
        assertTrue { inTheMiddle in before..after }

        testTimeSource += 500.days
        val farDate = clock.now()

        assertTrue { farDate > before }
        assertTrue { farDate > after }
        assertTrue { before < farDate }
        assertTrue { farDate == farDate }
        assertEquals(0, farDate.compareTo(farDate))
    }

    @Test
    fun testDurationArithmetic() {
        val testTimeSource = TestTimeSource()
        val clock = testTimeSource.toGMTClock()

        val now = clock.now()
        val plus10Secs = now + 10.seconds
        assertTrue { now < plus10Secs }
        assertEquals(now, plus10Secs - 10.seconds)
        assertEquals(10.seconds, plus10Secs - now)
    }
}
