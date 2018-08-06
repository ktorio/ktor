package io.ktor.client

import io.ktor.client.engine.*
import java.util.*


actual fun HttpClient(
    useDefaultTransformers: Boolean,
    block: HttpClientConfig.() -> Unit
): HttpClient = HttpClient(findAvailableFactory(), useDefaultTransformers, block)

interface HttpClientEngineContainer {
    val factory: HttpClientEngineFactory<*>
}

internal fun findAvailableFactory(): HttpClientEngineFactory<*> =
    ServiceLoader.load(HttpClientEngineContainer::class.java).toList().first().factory
