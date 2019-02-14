package io.ktor.client.tests.utils

import io.ktor.client.engine.*

/**
 * Perform test against all clients from dependencies.
 */
actual fun clientsTest(
    skipMissingPlatforms: Boolean,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = clientTest(block = block)
