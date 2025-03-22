/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Single [HttpClientCall] to [HttpResponse] mapper.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockRequestHandler)
 */
public typealias MockRequestHandler = suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData

/**
 * Scope for [MockRequestHandler].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockRequestHandleScope)
 */
public class MockRequestHandleScope(internal val callContext: CoroutineContext)

/**
 * [HttpClientEngineConfig] for [MockEngine].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngineConfig)
 */
public class MockEngineConfig : HttpClientEngineConfig() {

    /**
     * Request handlers.
     * Responses are given back in order they were added to [requestHandlers].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngineConfig.requestHandlers)
     */
    public val requestHandlers: MutableList<MockRequestHandler> = mutableListOf()

    /**
     * Should engine reuse handlers.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngineConfig.reuseHandlers)
     */
    public var reuseHandlers: Boolean = true

    /**
     * Add request handler to [MockEngine]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.mock.MockEngineConfig.addHandler)
     */
    public fun addHandler(handler: MockRequestHandler) {
        requestHandlers += handler
    }
}
