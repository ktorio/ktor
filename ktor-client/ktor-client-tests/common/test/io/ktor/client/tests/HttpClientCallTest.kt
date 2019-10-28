/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class HttpClientCallTest {
    @Test
    fun receiveWithExceptionTest() = clientTest(MockEngine) {
        config {
            engine {
                addHandler {
                    respondOk("content")
                }
            }
        }

        test { client ->
            client.responsePipeline.intercept(HttpResponsePipeline.Receive) { error("TestException") }
            val call = client.call("http://localhost") { method = HttpMethod.Get }
            val cause = assertFails { call.receive<String>() }
            assertTrue { cause.message!!.contains("TestException") }

            withTimeout(1000) {
                call.coroutineContext[Job]!!.join()
            }

            assertTrue { call.coroutineContext[Job]!!.isCompleted }
        }
    }
}
