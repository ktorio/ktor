/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.scheduler

import kotlinx.coroutines.*
import java.lang.Runnable
import java.time.*
import kotlin.coroutines.*

@InternalCoroutinesApi
class TestTimeProvidingCoroutineContext(private var now: LocalDateTime) : CoroutineDispatcher(), Delay, TimeProvider {

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        now = now.plusNanos(timeMillis * 1_000_000L)
        continuation.resume(Unit)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }

    override fun now(): LocalDateTime = now
}
