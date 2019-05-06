/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import org.junit.runner.*

/**
 * Helper interface to test client.
 */
@RunWith(ClientRunner::class)
actual abstract class ClientLoader {
    lateinit var engine: HttpClientEngineContainer

    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        skipPlatforms: List<String>,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        if ("jvm" in skipPlatforms) return
        clientTest(engine.factory, block)
    }
}
