/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*
import kotlin.coroutines.*

class Scheduler : Closeable {

    private val scheduleGroups = ConcurrentLinkedDeque<ScheduleGroup>()

    fun add(scheduleGroup: ScheduleGroup) {
        scheduleGroups.add(scheduleGroup)
    }

    override fun close() {
        scheduleGroups.removeIf {
            it.close()
            true
        }
    }

    class SchedulerOptions


    /**
     * Feature installation object
     */
    companion object Feature : ApplicationFeature<Application, SchedulerOptions, Scheduler> {
        override val key = AttributeKey<Scheduler>("Scheduler")

        override fun install(pipeline: Application, configure: SchedulerOptions.() -> Unit): Scheduler {
            val config = SchedulerOptions().also(configure)
            with(config) {
                val scheduler = Scheduler()
                // no config now
                return scheduler
            }
        }
    }
}

fun Application.schedule(
    dispatcher: CoroutineContext = Dispatchers.Default,
    schedules: ScheduleBuilder.() -> Unit
): Scheduler =
    feature(Scheduler).apply {
        add(ScheduleBuilder(dispatcher).apply { schedules() }.build())
    }
