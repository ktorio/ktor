/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.scheduler

import java.time.*
import java.time.temporal.*

interface Schedule {
    val repeat: Int
    val next: LocalDateTime.() -> LocalDateTime
    val task: suspend (LocalDateTime) -> Unit
}

class EverySecondSchedule(
    override val repeat: Int,
    override val task: suspend (LocalDateTime) -> Unit
) : Schedule {
    override val next: LocalDateTime.() -> LocalDateTime = { nextSecond() }

    private inline fun LocalDateTime.nextSecond(): LocalDateTime =
        truncatedTo(ChronoUnit.SECONDS)
            .plusSeconds(1)
}

class EveryMinuteSchedule(
    second: Int,
    override val repeat: Int,
    override val task: suspend (LocalDateTime) -> Unit
) : Schedule {
    override val next: LocalDateTime.() -> LocalDateTime = { nextMinute(second) }

    private inline fun LocalDateTime.nextMinute(second: Int): LocalDateTime {
        val next = with(LocalTime.of(hour, minute, second, 0))
        return if (this < next) next else next.plusMinutes(1)
    }
}

class EveryHourSchedule(
    minute: Int,
    second: Int,
    override val repeat: Int,
    override val task: suspend (LocalDateTime) -> Unit
) : Schedule {
    override val next: LocalDateTime.() -> LocalDateTime = { nextHour(minute, second) }

    private inline fun LocalDateTime.nextHour(minute: Int, second: Int): LocalDateTime {
        val next = with(LocalTime.of(hour, minute, second, 0))
        return if (this < next) next else next.plusHours(1)
    }
}

class EveryDaySchedule(
    hourOfDay: Int,
    minute: Int,
    second: Int,
    override val repeat: Int,
    override val task: suspend (LocalDateTime) -> Unit
) : Schedule {
    override val next: LocalDateTime.() -> LocalDateTime = { nextDay(hourOfDay, minute, second) }

    private inline fun LocalDateTime.nextDay(hourOfDay: Int, minute: Int, second: Int): LocalDateTime {
        val next = with(LocalTime.of(hourOfDay, minute, second, 0))
        return if (this < next) next else next.plusDays(1)
    }
}

class EveryWeekSchedule(
    dayOfWeek: DayOfWeek,
    hourOfDay: Int,
    minute: Int,
    second: Int,
    override val repeat: Int,
    override val task: suspend (LocalDateTime) -> Unit
) : Schedule {
    override val next: LocalDateTime.() -> LocalDateTime = { nextWeek(dayOfWeek, hourOfDay, minute, second) }

    private inline fun LocalDateTime.nextWeek(dayOfWeek: DayOfWeek, hourOfDay: Int, minute: Int, second: Int): LocalDateTime {
        val next = minusDays(1)
            .with(LocalTime.of(hourOfDay, minute, second, 0))
            .with(TemporalAdjusters.next(dayOfWeek))
        return if (this < next) next else next.plusDays(7)
    }
}
