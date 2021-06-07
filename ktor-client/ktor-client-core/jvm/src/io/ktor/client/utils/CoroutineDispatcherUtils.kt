/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import java.io.*
import kotlin.coroutines.*

/**
 * Creates [CoroutineDispatcher] based on thread pool of [threadCount] threads.
 */
@InternalAPI
public actual fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String
): CoroutineDispatcher = ClosableBlockingDispatcher(threadCount, dispatcherName)

/**
 * Creates [CoroutineDispatcher] based on thread pool of [threadCount] threads.
 */
@Suppress("unused")
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
public fun Dispatchers.fixedThreadPoolDispatcher(
    threadCount: Int,
    dispatcherName: String = "client-dispatcher"
): CoroutineDispatcher {
    return clientDispatcher(threadCount, dispatcherName)
}

internal actual fun checkCoroutinesVersion() {
}

@OptIn(InternalCoroutinesApi::class)
internal class ClosableBlockingDispatcher(
    threadCount: Int,
    dispatcherName: String
) : CoroutineDispatcher(), Closeable {
    private val _closed: AtomicBoolean = atomic(false)

    val closed: Boolean get() = _closed.value

    private val dispatcher = ExperimentalCoroutineDispatcher(threadCount, threadCount, dispatcherName)
    private val blocking = dispatcher.blocking(threadCount)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        return blocking.dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return blocking.isDispatchNeeded(context)
    }

    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        blocking.dispatchYield(context, block)
    }

    override fun close() {
        if (!_closed.compareAndSet(false, true)) return

        dispatcher.close()
        // blocking dispatcher is a view and doesn't allow close
    }
}
