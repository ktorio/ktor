/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * [HttpClientEngine] for writing tests without network.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine)
 */
public open class MockEngine internal constructor(
    override val config: MockEngineConfig,
    throwIfEmptyConfig: Boolean
) : HttpClientEngineBase("ktor-mock") {
    public constructor(config: MockEngineConfig) : this(config, throwIfEmptyConfig = true)

    override val supportedCapabilities: Set<HttpClientEngineCapability<out Any>> = setOf(
        HttpTimeoutCapability,
        WebSocketCapability,
        WebSocketExtensionsCapability
    )

    private val mutex = SynchronizedObject()
    private val contextState: CompletableJob = Job()

    private val _requestHistory: MutableList<HttpRequestData> = mutableListOf()
    private val _responseHistory: MutableList<HttpResponseData> = mutableListOf()

    private var invocationCount: Int = 0

    init {
        if (throwIfEmptyConfig) {
            check(config.requestHandlers.isNotEmpty()) {
                "No request handler provided in [MockEngineConfig], please provide at least one."
            }
        }
    }

    /**
     * History of executed requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.requestHistory)
     */
    public val requestHistory: List<HttpRequestData> get() = _requestHistory

    /**
     * History of sent responses.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.responseHistory)
     */
    public val responseHistory: List<HttpResponseData> get() = _responseHistory

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val handler = synchronized(mutex) {
            if (invocationCount >= config.requestHandlers.size) error("Unhandled ${data.url}")
            val handler = config.requestHandlers[invocationCount]

            invocationCount += 1
            if (config.reuseHandlers) {
                invocationCount %= config.requestHandlers.size
            }

            handler
        }

        val response = withContext(dispatcher + callContext) {
            handler(MockRequestHandleScope(callContext), data)
        }

        synchronized(mutex) {
            _requestHistory.add(data)
            _responseHistory.add(response)
        }

        return response
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            contextState.complete()
        }
    }

    /**
     * Create a [MockEngine] with an empty [MockEngineConfig] - meaning no request handlers are registered by
     * default. This means that you need to separately call [enqueue] to add one or more handlers before making any
     * requests.
     *
     * Most useful if you want to create an [io.ktor.client.HttpClient] instance before your test begins, and need
     * to specify behaviour on a per-test basis.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.Queue)
     */
    public class Queue(
        override val config: MockEngineConfig = MockEngineConfig().apply {
            // Every time a handler is called, it gets disposed. So make sure enough handlers are registered for
            // requests you intend to make!
            reuseHandlers = false
        },
    ) : MockEngine(config, throwIfEmptyConfig = false) {
        /**
         * Appends a new [MockRequestHandler], to be called/removed after any previous handlers have been consumed.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.Queue.enqueue)
         */
        public fun enqueue(handler: MockRequestHandler): Boolean = config.requestHandlers.add(handler)

        /**
         * Just a syntactic shortcut to [enqueue].
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.Queue.plusAssign)
         */
        public operator fun plusAssign(handler: MockRequestHandler) {
            enqueue(handler)
        }
    }

    public companion object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block))

        /**
         * Create [MockEngine] instance with single request handler.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngine.Companion.invoke)
         */
        public operator fun invoke(handler: MockRequestHandler): MockEngine = MockEngine(
            MockEngineConfig().apply {
                requestHandlers.add(handler)
            }
        )
    }
}
