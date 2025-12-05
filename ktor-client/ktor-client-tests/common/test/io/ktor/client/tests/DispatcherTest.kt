/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.test.base.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherTest : ClientLoader() {

    @Test
    fun `test the default dispatcher is IO - except web`() = clientTests(except("web:*")) {
        test { client ->
            assertEquals("Dispatchers.IO", client.engine.dispatcher.toString())
        }
    }

    @Test
    fun `test the default dispatcher is used on web`() = clientTests(only("web:*")) {
        test { client ->
            assertEquals(Dispatchers.Default, client.engine.dispatcher)
        }
    }

    @Test
    fun `test the dispatcher can be configured`() = clientTests {
        val customDispatcher = Dispatchers.Default.limitedParallelism(1)
        config {
            engine {
                dispatcher = customDispatcher
            }
        }

        test { client ->
            assertEquals(customDispatcher, client.engine.dispatcher)
        }
    }

    @Test
    fun `test HttpStatement_execute uses the engine dispatcher`() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/").execute {
                val currentDispatcher = currentCoroutineContext()[ContinuationInterceptor]
                assertEquals(client.engine.dispatcher, currentDispatcher)
            }
        }
    }

    @Test
    fun `test HttpStatement_body uses the engine dispatcher`() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/").body { _: String ->
                val currentDispatcher = currentCoroutineContext()[ContinuationInterceptor]
                assertEquals(client.engine.dispatcher, currentDispatcher)
            }
        }
    }
}
