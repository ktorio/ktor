/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class CommonHttpClientTest {
    @Test
    fun testHttpClientWithCustomEngineLifecycle() {
        val engine = MockEngine { respondOk() }
        val client = HttpClient(engine)
        client.close()

        // When the engine is provided by a user it should not be closed together with the client.
        assertTrue { engine.isActive }
    }

    @Test
    fun testHttpClientWithFactoryEngineLifecycle() {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respondOk() }
            }
        }
        val engine = client.engine
        client.close()

        // When the engine is provided by Ktor factory is should be closed together with the client.
        assertFalse { engine.isActive }
    }
}
