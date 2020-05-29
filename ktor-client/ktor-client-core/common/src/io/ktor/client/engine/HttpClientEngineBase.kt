/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Abstract implementation of [HttpClientEngine] responsible for lifecycle control of [dispatcher] and
 * [coroutineContext] as well as proper call context management. Should be considered as the best parent class for
 * custom [HttpClientEngine] implementations.
 */
abstract class HttpClientEngineBase(private val engineName: String) : HttpClientEngine {

    override val coroutineContext: CoroutineContext by lazy {
        SilentSupervisor() + dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
        (coroutineContext[Job] as CompletableJob).apply {
            complete()
            invokeOnCompletion {
                dispatcher.close()
            }
        }
    }
}

/**
 * Exception that indicates that client engine is already closed.
 */
class ClientEngineClosedException(override val cause: Throwable? = null) :
    IllegalStateException("Client already closed")

/**
 * Close [CoroutineDispatcher] if it's [Closeable].
 */
private fun CoroutineDispatcher.close() = try {
    (this as? Closeable)?.close()
} catch (ignore: Throwable) {
    // Some closeable dispatchers like Dispatchers.IO can't be closed.
}
