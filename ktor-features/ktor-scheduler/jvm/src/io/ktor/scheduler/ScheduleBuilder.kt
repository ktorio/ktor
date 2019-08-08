/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import java.time.*
import kotlin.coroutines.*

class ScheduleBuilder internal constructor(
    private val dispatcher: CoroutineContext
) {

    private val schedules = mutableListOf<Schedule>()

    fun everySecond(repeat: Int = 0, action: suspend (LocalDateTime) -> Unit) {
        schedules += EverySecondSchedule(repeat, action)
    }

    fun everyMinute(second: Int = 0, repeat: Int = 0, action: suspend (LocalDateTime) -> Unit) {
        schedules += EveryMinuteSchedule(second, repeat, action)
    }

    fun everyHour(minute: Int = 0, second: Int = 0, repeat: Int = 0, action: suspend (LocalDateTime) -> Unit) {
        schedules += EveryHourSchedule(minute, second, repeat, action)
    }

    fun everyDay(hourOfDay: Int, minute: Int = 0, second: Int = 0, repeat: Int = 0, action: suspend (LocalDateTime) -> Unit) {
        schedules += EveryDaySchedule(hourOfDay, minute, second, repeat, action)
    }

    fun everyWeek(
        dayOfWeek: DayOfWeek,
        hourOfDay: Int,
        minute: Int = 0,
        second: Int = 0,
        repeat: Int = 0,
        action: suspend (LocalDateTime) -> Unit
    ) {
        schedules += EveryWeekSchedule(dayOfWeek, hourOfDay, minute, second, repeat, action)
    }

    fun everyWeek(
        daysOfWeek: Set<DayOfWeek>,
        hourOfDay: Int,
        minute: Int = 0,
        second: Int = 0,
        repeat: Int = 0,
        action: suspend (LocalDateTime) -> Unit
    ) {
        daysOfWeek.forEach {
            everyWeek(it, hourOfDay, minute, second, repeat, action)
        }
    }

    fun build(): ScheduleGroup = ScheduleGroup(dispatcher, schedules)

}
