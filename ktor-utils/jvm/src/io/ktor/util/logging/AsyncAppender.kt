/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

/**
 * Asynchronous appender with record dropping.
 * Formats and logs records asynchronously. Enqueues records with the specified [capacity].
 * If the downstream is unable to handle messages in time, the queue overflows and extra events are discarded.
 *
 * @property capacity for the internal record queue
 * @property parent coroutines parent job for the asynchronous appender job.
 */
class AsyncAppender(private val delegate: Appender, val capacity: Int = 1000, val parent: Job? = null) : Appender {
    @UseExperimental(ObsoleteCoroutinesApi::class)
    private val task = GlobalScope.actor<LogRecord.ManuallyManagedReference>(
        context = parent ?: EmptyCoroutineContext,
        start = CoroutineStart.LAZY,
        capacity = capacity
    ) {
        while (isActive) {
            val message = receiveOrNull() ?: break
            message.consume {
                delegate.append(it)
            }

            while (!isEmpty) {
                val e = poll() ?: break
                e.consume {
                    delegate.append(it)
                }
            }

            delegate.flush()
        }
    }

    override fun append(record: LogRecord) {
        task.offer(record.defer())
    }

    override fun flush() {
    }

    /**
     * Gracefully close asynchronous appender. All extra log records after this invocation will be discarded.
     */
    fun close() {
        task.close()
    }
}
