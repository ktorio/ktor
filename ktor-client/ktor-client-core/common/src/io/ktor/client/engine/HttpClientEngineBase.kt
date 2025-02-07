/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Abstract base implementation of [HttpClientEngine], providing lifecycle management for the [dispatcher]
 * and [coroutineContext], as well as proper handling of call contexts.
 *
 * This class is designed to simplify the creation of custom [HttpClientEngine] implementations by
 * handling common functionality such as:
 * - Managing the [dispatcher] for I/O operations.
 * - Setting up a structured [coroutineContext] with a custom name for easier debugging.
 * - Ensuring proper resource cleanup when the engine is closed.
 *
 * Developers creating custom HTTP client engines are encouraged to use this class as their parent,
 * as it handles much of the boilerplate related to engine lifecycle and coroutine management.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.HttpClientEngineBase)
 *
 * @param engineName The name of the engine, used for debugging and context naming.
 *
 * Example:
 * ```kotlin
 * class MyCustomHttpClientEngine : HttpClientEngineBase("MyCustomEngine") {
 *     override suspend fun execute(data: HttpRequestData): HttpResponseData {
 *         // Implementation of request execution
 *     }
 * }
 * ```
 */
public abstract class HttpClientEngineBase(private val engineName: String) : HttpClientEngine {
    private val closed = atomic(false)

    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        SilentSupervisor() + dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val requestJob = coroutineContext[Job] as? CompletableJob ?: return

        requestJob.complete()
    }
}

/**
 * An exception indicating that the client's engine is already closed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ClientEngineClosedException)
 */
public class ClientEngineClosedException(override val cause: Throwable? = null) :
    IllegalStateException("Client already closed")

internal expect fun ioDispatcher(): CoroutineDispatcher
