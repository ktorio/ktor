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
}
