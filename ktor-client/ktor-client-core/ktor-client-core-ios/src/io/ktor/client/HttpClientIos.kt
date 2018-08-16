package io.ktor.client

import io.ktor.client.engine.ios.*

actual fun HttpClient(
    useDefaultTransformers: Boolean,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(IosClient(), useDefaultTransformers, block)
