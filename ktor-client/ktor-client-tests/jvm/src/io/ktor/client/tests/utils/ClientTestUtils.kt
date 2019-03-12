package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import java.util.*

/**
 * Perform test against all clients from dependencies.
 */
actual fun clientsTest(
    skipMissingPlatforms: Boolean,
    skipPlatforms: List<String>,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
): Unit {
    if ("jvm" in skipPlatforms) return

    val engines: List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
        ServiceLoader.load(it, it.classLoader).toList()
    }

    check(skipMissingPlatforms || engines.isNotEmpty()) { "No test engines provided." }

    engines.forEach {
        clientTest(it.factory, block)
    }
}
