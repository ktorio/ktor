/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Base jvm implementation for [HttpClientEngine]
 */
@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Use HttpClientEngineBase instead.",
    replaceWith = ReplaceWith("HttpClientEngineBase"),
    level = DeprecationLevel.ERROR
)
public abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    private val clientContext = SilentSupervisor()
    private val _dispatcher by lazy {
        Executors.newFixedThreadPool(config.threadsCount) {
            Thread(it).apply {
                isDaemon = true
            }
        }.asCoroutineDispatcher()
    }

    @OptIn(InternalCoroutinesApi::class)
    override val dispatcher: CoroutineDispatcher
        get() = _dispatcher

    @OptIn(InternalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        _dispatcher + clientContext + CoroutineName("$engineName-context")
    }

    /**
     * Create [CoroutineContext] to execute call.
     */
    @OptIn(InternalCoroutinesApi::class)
    protected suspend fun createCallContext(): CoroutineContext {
        val callJob = Job(clientContext[Job])
        val callContext = coroutineContext + callJob

        val parentCoroutineJob = currentContext()[Job]
        val onParentCancelCleanupHandle = parentCoroutineJob?.invokeOnCompletion(
            onCancelling = true
        ) { cause ->
            if (cause != null) callContext.cancel()
        }

        callJob.invokeOnCompletion {
            onParentCancelCleanupHandle?.dispose()
        }

        return callContext
    }

    override fun close() {
        val job = clientContext[Job] as CompletableJob

        job.complete()
        job.invokeOnCompletion {
            _dispatcher.close()
        }
    }
}

private suspend inline fun currentContext() = coroutineContext
