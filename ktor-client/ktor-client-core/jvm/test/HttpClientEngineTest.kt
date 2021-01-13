/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.utils.*
import io.ktor.client.engine.mock.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.test.*

class HttpClientEngineTest {

    @Test
    fun testEarlyCancellation() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondOk()
                }
            }
        }

        val dispatcher = client.engine.dispatcher as ClosableBlockingDispatcher
        assertFalse(dispatcher.closed)

        val first = Job()
        val second = client.async {
            first.join()
            assertFalse(dispatcher.closed)
        }

        client.close()
        first.complete()
        second.await()
        assertTrue(dispatcher.closed)
    }
}
