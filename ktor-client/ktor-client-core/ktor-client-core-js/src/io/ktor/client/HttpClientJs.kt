package io.ktor.client

import io.ktor.client.engine.js.*

actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(JsClient(), block)
