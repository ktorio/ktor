package io.ktor.client

import io.ktor.client.engine.curl.*

actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(CurlClient(), block)
