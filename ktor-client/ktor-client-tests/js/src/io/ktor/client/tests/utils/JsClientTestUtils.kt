package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.*

/**
 * Perform test against all clients from dependencies.
 */
actual fun clientsTest(
    skipMissingPlatforms: Boolean,
    skipPlatforms: List<String>,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
): dynamic = if ("js" in skipPlatforms) GlobalScope.async { }.asPromise() else clientTest(block = block)

