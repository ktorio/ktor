/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import org.junit.*
import org.junit.Assert.*
import java.time.*

class ScheduleTest {

    @Test
    fun everySecondScheduleTest() {
        val schedule = EverySecondSchedule(0) {}

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 0))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 1, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 314))
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 1, 0, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(1, 0, 0, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 8).atTime(0, 0, 0, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.JANUARY, 1).atTime(0, 0, 0, 0),
            schedule.next(LocalDate.of(2019, Month.DECEMBER, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.SEPTEMBER, 1).atTime(0, 0, 0, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.FEBRUARY, 29).atTime(0, 0, 0, 0),
            schedule.next(LocalDate.of(2020, Month.FEBRUARY, 28).atTime(23, 59, 59, 314))
        )

    }

    @Test
    fun everyMinuteScheduleTest() {
        val schedule = EveryMinuteSchedule(second = 10, repeat = 0) {}

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 0))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 314))
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 9, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 1, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 10, 0))
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 1, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(1, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 8).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.JANUARY, 1).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.DECEMBER, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.SEPTEMBER, 1).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.FEBRUARY, 29).atTime(0, 0, 10, 0),
            schedule.next(LocalDate.of(2020, Month.FEBRUARY, 28).atTime(23, 59, 59, 314))
        )
    }

    @Test
    fun everyHourScheduleTest() {
        val schedule = EveryHourSchedule(minute = 5, second = 10, repeat = 0) {}

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 0))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 314))
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 9, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(1, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 10, 0))
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 7).atTime(1, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 8).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.JANUARY, 1).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.DECEMBER, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2019, Month.SEPTEMBER, 1).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 31).atTime(23, 59, 59, 314))
        )
        assertEquals(
            LocalDate.of(2020, Month.FEBRUARY, 29).atTime(0, 5, 10, 0),
            schedule.next(LocalDate.of(2020, Month.FEBRUARY, 28).atTime(23, 59, 59, 314))
        )
    }

    @Test
    fun everyWeekScheduleTest() {
        val schedule = EveryWeekSchedule(dayOfWeek = DayOfWeek.TUESDAY, hourOfDay = 2, minute = 5, second = 10, repeat = 0) {}

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 7).atTime(0, 0, 0, 314)) // WED
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 8).atTime(0, 0, 0, 314)) // THU
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 9).atTime(0, 0, 0, 314)) // FRI
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 10).atTime(0, 0, 0, 314)) // SAT
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 11).atTime(0, 0, 0, 314)) // SUN
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 12).atTime(0, 0, 0, 314)) // MON
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 13).atTime(0, 0, 0, 314)) // TUE
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 20).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 14).atTime(0, 0, 0, 314)) // WED
        )

        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 9, 314)) // TUE
        )
        assertEquals(
            LocalDate.of(2019, Month.AUGUST, 20).atTime(2, 5, 10, 0),
            schedule.next(LocalDate.of(2019, Month.AUGUST, 13).atTime(2, 5, 10, 0)) // TUE
        )

    }
}
