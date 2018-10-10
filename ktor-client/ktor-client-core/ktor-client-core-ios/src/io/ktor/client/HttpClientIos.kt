package io.ktor.client

import io.ktor.client.engine.ios.*

actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(IosClient(), block)
