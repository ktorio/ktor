/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kron.Reactor
import kron.RingTimer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs coroutines on [reactor]'s thread, each resumption posted to the reactor and run on its loop's next pass, from
 * that thread too: a coroutine resumed by another one runs after it, not inside it, the order Ktor's code assumes (a
 * WebSocket reader closes `incoming`, then sends its Close). kron resumes its own reads and flushes inline, without
 * the dispatcher, so a call that only reads and writes is never posted.
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class ReactorDispatcher(private val reactor: Reactor) : CoroutineDispatcher(), Delay {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        reactor.post { block.run() }
    }

    // delay() as an IORING_OP_TIMEOUT on the reactor's own ring: the deadline leaves with the reactor's next
    // submission, and the completion resumes the coroutine there - no timer thread, no handoff. A cancelled wait
    // still runs out in the kernel; its resumption is then ignored.
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timer = RingTimer(reactor)
        suspend { timer.delay(timeMillis) }.startCoroutine(
            Continuation(EmptyCoroutineContext) {
                with(continuation) { resumeUndispatched(Unit) }
            },
        )
    }

    override fun toString(): String = "ReactorDispatcher(${reactor.id})"
}
