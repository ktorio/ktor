/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.scheduler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.time.*

internal interface TimeProvider {
    fun now(): LocalDateTime
}

internal open class SystemTimeProvider(
    private val clock: Clock
) : TimeProvider {
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
}

internal object DefaultTimeProvider : SystemTimeProvider(Clock.system(ZoneId.systemDefault()))

internal suspend fun TimeProvider.delayScheduled(repeat: Int, channel: SendChannel<LocalDateTime>, next: LocalDateTime.() -> LocalDateTime) {
    suspend fun delayAndSend() {
        val now = now()
        val deadline = now.next()
        val delay = (deadline - now).coerceAtLeast(0)
        delayByNanos(delay)
        channel.send(now())
    }

    if (repeat <= 0) {
        while (true) {
            delayAndSend()
        }
    } else {
        for (i in 0 until repeat) {
            delayAndSend()
        }
    }
}

internal const val MS_TO_NS = 1_000_000
internal const val SEC_TO_NS = 1_000_000_000L
private val systemZoneId = ZoneId.systemDefault()

internal operator fun LocalDateTime.minus(from: LocalDateTime): Long {
    fun LocalDateTime.toEpochNano(): Long = atZone(systemZoneId).toInstant().run {
        epochSecond * SEC_TO_NS + nano
    }
    return this.toEpochNano() - from.toEpochNano()
}

private suspend inline fun delayByNanos(timeNanos: Long) = delay(timeNanos / MS_TO_NS)

