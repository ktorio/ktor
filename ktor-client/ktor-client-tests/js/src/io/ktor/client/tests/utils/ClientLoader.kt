/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.*

/**
 * Helper interface to test client.
 */
actual abstract class ClientLoader {
    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        skipPlatforms: List<String>,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): dynamic = if ("js" in skipPlatforms) GlobalScope.async {}.asPromise() else clientTest {
        withTimeout(30 * 1000) {
            block()
        }
    }

    actual fun dumpCoroutines() {
        error("Debug probes unsupported[js]")
    }
}
