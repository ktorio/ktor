package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*


class CIOEngine : HttpClientEngine {
    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            CIOHttpRequest(call, builder)

    override fun close() {}
}
