/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class HttpClientCallTest {
    @Test
    fun receiveWithExceptionTest() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    respondOk("content")
                }
            }
        }

        test { client ->
            client.responsePipeline.intercept(HttpResponsePipeline.Receive) { error("TestException") }
            val call = client.prepareGet("http://localhost")
            val cause = assertFails { call.receive<String>() }
            assertTrue { cause.message!!.contains("TestException") }
        }
    }
}
