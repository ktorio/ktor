/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*
import kotlin.test.*

class ClientPipelinesTest : ClientLoader() {
    @OptIn(InternalAPI::class)
    @Test
    fun testCanAddHeaders() = clientTests {
        config {
            install("attr-test") {
                receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                    val headers = buildHeaders {
                        appendAll(response.headers)
                        append(HttpHeaders.WWWAuthenticate, "Bearer")
                    }
                    val responseWithHeaders = object : HttpResponse by response {
                        override val headers get() = headers
                    }

                    proceedWith(responseWithHeaders)
                }
            }
        }

        test { client ->
            val response = client.get("$TEST_SERVER/content/hello")
            assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        }
    }
}
