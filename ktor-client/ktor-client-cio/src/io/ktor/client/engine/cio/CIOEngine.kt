package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*


class CIOEngine(config: HttpClientEngineConfig) : HttpClientEngine {
    private val dispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            CIOHttpRequest(call, dispatcher, builder)

    override fun close() {}
}
