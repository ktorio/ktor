/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*

// TODO
//  * JVM implementation don't work on K/N out of the box
//  * K/JS doesn't support extending lambdas like CompletionHandler = (cause: Throwable?) -> Unit

/**
 * It provides ability to cancel jobs and schedule coroutine with timeout. Unlike regular withTimeout
 * this implementation is never scheduling timer tasks but only checks for current time. This makes timeout measurement
 * much cheaper and doesn't require any watchdog thread.
 *
 * There are two limitations:
 *  - timeout period is fixed
 *  - job cancellation is not guaranteed if no new jobs scheduled
 *
 *  The last one limitation is generally unacceptable
 *  however in the particular use-case (closing IDLE connection) it is just fine
 *  as we really don't care about stalling IDLE connections if there are no more incoming
 */
@InternalAPI
@Suppress("KDocMissingDocumentation")
public expect class WeakTimeoutQueue(
    timeoutMillis: Long,
    clock: () -> Long = { getTimeMillis() }
) {
    public val timeoutMillis: Long

    /**
     * Cancel all registered timeouts
     */
    public fun cancel()

    /**
     * Process and cancel all jobs that are timed out
     */
    public fun process()

    /**
     * Counts registered jobs, for testing purpose only
     */
    internal fun count(): Int

    /**
     * Execute [block] and cancel if doesn't complete in time.
     * Unlike the regular kotlinx.coroutines withTimeout,
     * this also checks for cancellation first and fails immediately.
     */
    public suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T
}
