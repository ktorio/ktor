package io.ktor.client.tests.utils

import io.ktor.client.engine.*

/**
 * Perform test against all clients from dependencies.
 */
actual fun clientsTest(
    skipMissingPlatforms: Boolean,
    skipPlatforms: List<String>,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) {
    if ("native" in skipPlatforms) return

    check(skipMissingPlatforms || engines.isNotEmpty()) { "No test engines provided." }
    engines.forEach {
        clientTest(it, block)
    }
}
