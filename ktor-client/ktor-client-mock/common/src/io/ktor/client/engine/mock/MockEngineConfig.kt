package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*

/**
 * Single [HttpClientCall] to [HttpResponse] mapper.
 */
typealias MockRequestHandler = suspend HttpClientCall.(request: MockHttpRequest) -> HttpResponse

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
