/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Single [HttpClientCall] to [HttpResponse] mapper.
 */
typealias MockRequestHandler = suspend (request: HttpRequestData) -> HttpResponseData

/**
 * [HttpClientEngineConfig] for [MockEngine].
 */
class MockEngineConfig : HttpClientEngineConfig() {

    /**
     * Request handlers.
     * Responses are given back in order they were added to [requestHandlers].
     */
    val requestHandlers: MutableList<MockRequestHandler> = mutableListOf()

    /**
     * Should engine reuse handlers.
     */
    var reuseHandlers: Boolean = true

    /**
     * Add request handler to [MockEngine]
     */
    fun addHandler(handler: MockRequestHandler) {
        requestHandlers += handler
    }
}
