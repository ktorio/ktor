/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CallValidatorTest {
    @Test
    fun testAllExceptionHandlers() = testWithEngine(MockEngine) {
        var firstHandler = 0
        var secondHandler = 0

        config {
            engine {
                addHandler { respondOk() }
            }
            HttpResponseValidator {
                handleResponseException {
                    firstHandler++
                    assertTrue(it is CallValidatorTestException)
                }

                handleResponseException {
                    secondHandler++
                    assertTrue(it is CallValidatorTestException)
                }
            }
        }

        test { client ->
            client.responsePipeline.intercept(HttpResponsePipeline.Transform) { throw CallValidatorTestException() }

            var thirdHandler = false
            try {
                client.get<String>()
            } catch (_: CallValidatorTestException) {
                thirdHandler = true
            }

            assertEquals(1, firstHandler)
            assertEquals(1, secondHandler)
            assertTrue(thirdHandler)
        }
    }

    @Test
    fun testExceptionFromEngine() = testWithEngine(MockEngine) {
        var handleTriggered = 0
        config {
            engine {
                addHandler { throw CallValidatorTestException() }
            }
            HttpResponseValidator {
                handleResponseException {
                    assertTrue(it is CallValidatorTestException)
                    handleTriggered++
                }
            }
        }
        test { client ->
            try {
                client.request<HttpResponse>()
            } catch (_: CallValidatorTestException) {
            }

            assertEquals(1, handleTriggered)
        }
    }

    @Test
    fun testExceptionFromReceivePipeline() = testWithEngine(MockEngine) {
        var handleTriggered = false
        config {
            engine {
                addHandler { respondOk() }
            }
            HttpResponseValidator {
                handleResponseException {
                    assertTrue(it is CallValidatorTestException)
                    handleTriggered = true
                }
            }
        }
        test { client ->
            client.responsePipeline.intercept(HttpResponsePipeline.Transform) { throw CallValidatorTestException() }
            try {
                client.get<String>()
            } catch (_: CallValidatorTestException) {
            }

            assertTrue(handleTriggered)
        }
    }

    @Test
    fun testMergeMultipleConfigs() = testWithEngine(MockEngine) {
        var firstHandler = 0
        var secondHandler = 0

        config {
            engine {
                addHandler { respondOk() }
            }
            HttpResponseValidator {
                handleResponseException {
                    firstHandler++
                    assertTrue(it is CallValidatorTestException)
                }
            }

            HttpResponseValidator {
                handleResponseException {
                    secondHandler++
                    assertTrue(it is CallValidatorTestException)
                }
            }
        }

        test { client ->
            client.responsePipeline.intercept(HttpResponsePipeline.Transform) { throw CallValidatorTestException() }

            var thirdHandler = false
            try {
                client.get<String>()
            } catch (_: CallValidatorTestException) {
                thirdHandler = true
            }

            assertEquals(1, firstHandler)
            assertEquals(1, secondHandler)
            assertTrue(thirdHandler)
        }
    }

    @Test
    fun testResponseValidation() = testWithEngine(MockEngine) {
        var validator = 0
        config {
            engine {
                addHandler {
                    val status = HttpStatusCode(42, "Awesome code")
                    respond("Awesome response", status)
                }
            }

            HttpResponseValidator {
                validateResponse {
                    assertEquals(42, it.status.value)
                    validator++
                }
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Awesome response", response)
            assertEquals(1, validator)
        }
    }
}

internal class CallValidatorTestException : Throwable()
