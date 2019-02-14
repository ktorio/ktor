package io.ktor.client.tests.utils

import io.ktor.client.engine.*

/**
 * Perform test against all clients from dependencies.
 */
actual fun clientsTest(
    skipMissingPlatforms: Boolean,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) {
    check(skipMissingPlatforms || engines.isNotEmpty()) { "No test engines provided." }
    engines.forEach { clientTest(it, block) }
}
