/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.cio

import io.ktor.server.netty.*
import io.ktor.util.internal.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class NettyRequestQueue(internal val readLimit: Int, internal val runningLimit: Int) {
    init {
        require(readLimit > 0) { "readLimit should be positive: $readLimit" }
        require(runningLimit > 0) { "executeLimit should be positive: $runningLimit" }
    }

    private val incomingQueue = Channel<CallElement>(Channel.UNLIMITED)

    val elements: ReceiveChannel<CallElement> = incomingQueue

    fun schedule(call: NettyApplicationCall) {
        val element = CallElement(call)
        try {
            incomingQueue.offer(element)
        } catch (t: Throwable) {
            element.tryDispose()
        }
    }

    fun close() {
        incomingQueue.close()
    }

    fun cancel() {
        incomingQueue.close()

        while (true) {
            incomingQueue.poll()?.tryDispose() ?: break
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun canRequestMoreEvents(): Boolean = incomingQueue.isEmpty

    internal class CallElement(val call: NettyApplicationCall) : LockFreeLinkedListNode() {
        private val scheduled = atomic(0)

        private val message: Job = call.response.responseMessage

        val isCompleted: Boolean get() = message.isCompleted

        fun ensureRunning(): Boolean {
            scheduled.update { value ->
                when (value) {
                    0 -> 1
                    1 -> return true
                    else -> return false
                }
            }

            call.context.fireChannelRead(call)
            return true
        }

        fun tryDispose() {
            if (scheduled.compareAndSet(0, 2)) {
                call.dispose()
            }
        }
    }
}
