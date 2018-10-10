package io.ktor.client

import io.ktor.client.engine.*
import java.util.*


actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

interface HttpClientEngineContainer {
    val factory: HttpClientEngineFactory<*>
}

/**
 * Workaround for dummy android [ClassLoader].
 */
private val engines: List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
    ServiceLoader.load(it, it.classLoader).toList()
}

private val FACTORY = engines.firstOrNull()?.factory
    ?: error("Failed to find HttpClientEngineContainer in classpath via ServiceLoader")