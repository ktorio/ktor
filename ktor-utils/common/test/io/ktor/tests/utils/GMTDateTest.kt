package io.ktor.tests.utils

import io.ktor.util.date.*
import kotlin.test.*

class GMTDateTest {

    @Test
    fun constructionTest() {
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
        assertEquals(first.timestamp / 1000 , third.timestamp / 1000)
    }

    @Test
    fun testComparison() {
        val before = GMTDate(1L)
        val after = GMTDate(3L)
        val inTheMiddle = GMTDate(2L)

        assertTrue { before < after }
        assertTrue { inTheMiddle in before .. after }

        val farDate = GMTDate(after.timestamp * 1000)

        assertTrue { farDate > before }
        assertTrue { farDate > after }
        assertTrue { before < farDate }
        assertTrue { farDate == farDate }
        assertEquals(0, farDate.compareTo(farDate))
    }
}
