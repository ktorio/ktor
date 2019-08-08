/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.time.*
import kotlin.coroutines.*

class ScheduleGroup(
    dispatcher: CoroutineContext,
    schedules: List<Schedule>
)  : CoroutineScope, Closeable {
    override val coroutineContext: CoroutineContext = dispatcher// + SupervisorJob()

    private val time = dispatcher as? TimeProvider ?: DefaultTimeProvider
    private val tickers: List<ReceiveChannel<LocalDateTime>> = schedules.map { it.start() }

    override fun close() {
        tickers.forEach { it.cancel() }
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    fun Schedule.start(): ReceiveChannel<LocalDateTime> {
        val ticker = time.scheduleTicker(repeat, next)

        launch {
            for (event in ticker) {
                task.invoke(event)
            }
        }
        return ticker
    }

    internal fun TimeProvider.scheduleTicker(
        repeat: Int,
        next: LocalDateTime.() -> LocalDateTime
    ) = produce<LocalDateTime>(coroutineContext, capacity = Channel.RENDEZVOUS) {
        delayScheduled(repeat, channel, next)
    }
}
