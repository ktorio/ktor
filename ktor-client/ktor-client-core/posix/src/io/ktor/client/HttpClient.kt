package io.ktor.client

import io.ktor.client.engine.ios.*

@HttpClientDsl
actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(engines.first(), block)
