/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import kotlinx.coroutines.*

/**
 * Helper interface to test client.
 */
public actual abstract class ClientLoader {
    /**
     * Perform test against all clients from dependencies.
     */
    public actual fun clientTests(
        skipEngines: List<String>,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ): dynamic = {
        val skipEnginesLowerCase = skipEngines.map { it.toLowerCase() }
        if (skipEnginesLowerCase.contains("js")) GlobalScope.async {}.asPromise() else testWithEngine(Js) {
            withTimeout(30 * 1000) {
                block()
            }
        }
    }()

    public actual fun dumpCoroutines() {
        error("Debug probes unsupported[js]")
    }
}
