/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Base jvm implementation for [HttpClientEngine]
 */
@Suppress("KDocMissingDocumentation")
abstract class HttpClientJvmEngine(engineName: String) : HttpClientEngine {
    abstract override val config: HttpClientJvmEngineConfig

    private val clientContext = SupervisorJob()
    protected val executorService: ExecutorService by lazy {
        Executors.newFixedThreadPool(config.threadsCount, KtorThreadFactory(config.daemon))
    }
    private val _dispatcher by lazy {
        executorService.asCoroutineDispatcher()
    }

    @UseExperimental(InternalCoroutinesApi::class)
    override val dispatcher: CoroutineDispatcher
        get() = _dispatcher

    @UseExperimental(InternalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        _dispatcher + clientContext + CoroutineName("$engineName-context")
    }

    /**
     * Create [CoroutineContext] to execute call.
     */
    protected fun createCallContext(): CoroutineContext = coroutineContext + Job(clientContext)

    override fun close() {
        clientContext.complete()

        clientContext.invokeOnCompletion {
            _dispatcher.close()
        }
    }

    protected class KtorThreadFactory(private val daemon: Boolean) : ThreadFactory {
        private val group = System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup
        private val namePrefix = "ktor-${poolNumber.getAndIncrement()}-thread-"
        private val threadNumber = atomic(1)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0)
            thread.isDaemon = daemon
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }

        companion object {
            private val poolNumber = atomic(1)
        }
    }
}
