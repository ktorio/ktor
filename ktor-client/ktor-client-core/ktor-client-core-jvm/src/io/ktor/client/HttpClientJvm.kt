package io.ktor.client

import io.ktor.client.engine.*
import java.util.*


actual fun HttpClient(
    useDefaultTransformers: Boolean,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, useDefaultTransformers, block)

interface HttpClientEngineContainer {
    val factory: HttpClientEngineFactory<*>
}

private val FACTORY = ServiceLoader.load(HttpClientEngineContainer::class.java)
    .toList()
    .firstOrNull()
    ?.factory ?: error("Failed to find HttpClientEngineContainer in classpath via ServiceLoader")