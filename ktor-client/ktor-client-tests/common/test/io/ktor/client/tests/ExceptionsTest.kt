/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlin.test.*
import kotlin.coroutines.*
import kotlinx.coroutines.*

class ExceptionsTest : ClientLoader() {

    @Test
    fun testReadResponseFromException() = testSuspend {
        if (PlatformUtils.IS_NATIVE) return@testSuspend

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(HttpStatusCode.BadRequest)
                }
            }
        }

        try {
            client.get<String>("www.google.com")
        } catch (exception: ResponseException) {
            val text = exception.response?.readText()
            assertEquals(HttpStatusCode.BadRequest.description, text)
        }
    }

    @Test
    fun testErrorOnResponseCoroutine() = clientTests {
        config {
            test { client ->
                val requestBuilder = HttpRequestBuilder()
                requestBuilder.url.takeFrom("$TEST_SERVER/download/infinite")

                assertFailsWith<IllegalStateException> {
                    client.get<HttpStatement>(requestBuilder).execute { response ->
                        try {
                            CoroutineScope(response.coroutineContext)
                                .launch { throw IllegalStateException("failed on receive") }
                                .join()
                        } catch (e: Exception) {
                        }
                        response.content.toByteArray()
                    }
                }

                assertTrue(requestBuilder.executionContext[Job]!!.isActive)
            }
        }
    }
}
