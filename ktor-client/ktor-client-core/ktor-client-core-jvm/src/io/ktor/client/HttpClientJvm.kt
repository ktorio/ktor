package io.ktor.client

import io.ktor.client.engine.*
import java.util.*


fun HttpClient(
    useDefaultTransformers: Boolean = true,
    block: HttpClientConfig.() -> Unit = {}
) = HttpClient(findAvailableFactory(), useDefaultTransformers, block)

interface HttpClientEngineContainer {
    val factory: HttpClientEngineFactory<*>
}

internal fun findAvailableFactory(): HttpClientEngineFactory<*> =
    ServiceLoader.load(HttpClientEngineContainer::class.java).toList().first().factory

