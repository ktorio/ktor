/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.concurrent.*
import kotlin.test.*

class CallValidatorTest {
    private var firstHandler by shared(0)
    private var secondHandler by shared(0)
    private var handleTriggered by shared(false)
    private var validator by shared(0)

    @Test
    fun testAllExceptionHandlers() = testWithEngine(MockEngine) {
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
                client.get {}.body<String>()
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
        config {
            engine {
                addHandler { throw CallValidatorTestException() }
            }
            HttpResponseValidator {
                handleResponseException {
                    assertTrue(it is CallValidatorTestException)
                    firstHandler++
                }
            }
        }
        test { client ->
            try {
                client.request()
            } catch (_: CallValidatorTestException) {
            }

            assertEquals(1, firstHandler)
        }
    }

    @Test
    fun testExceptionFromResponsePipeline() = testWithEngine(MockEngine) {
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
                client.get {}.body<String>()
            } catch (_: CallValidatorTestException) {
            }

            assertTrue(handleTriggered)
        }
    }

    @Test
    fun testExceptionFromReceivePipeline() = testWithEngine(MockEngine) {
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
            client.receivePipeline.intercept(HttpReceivePipeline.State) { throw CallValidatorTestException() }
            try {
                client.get {}.body<String>()
            } catch (_: CallValidatorTestException) {
            }

            assertTrue(handleTriggered)
        }
    }

    @Test
    fun testMergeMultipleConfigs() = testWithEngine(MockEngine) {
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

            try {
                client.get {}.body<String>()
            } catch (_: CallValidatorTestException) {
                handleTriggered = true
            }

            assertEquals(1, firstHandler)
            assertEquals(1, secondHandler)
            assertTrue(handleTriggered)
        }
    }

    @Test
    fun testResponseValidation() = testWithEngine(MockEngine) {
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
            val response = client.get {}.body<String>()
            assertEquals("Awesome response", response)
            assertEquals(1, validator)
        }
    }

    @Test
    fun testResponseValidationOnHttpResponse() = testWithEngine(MockEngine) {
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
            client.get {}
            assertEquals(1, validator)
        }
    }

    @Test
    fun testResponseValidationThrowsResponseException() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
        }

        test { client ->
            try {
                client.get {}
                fail("Should fail")
            } catch (cause: ResponseException) {
                assertEquals(900, cause.response.status.value)
                assertEquals("Awesome response", cause.response.body())
            }
        }
    }

    @Test
    fun testResponseValidationThrowsResponseExceptionOnReceive() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
        }

        test { client ->
            try {
                client.get {}.body<String>()
                fail("Should fail")
            } catch (cause: ResponseException) {
                assertEquals(900, cause.response.status.value)
                assertEquals("Awesome response", cause.response.body())
            }
        }
    }

    @Test
    fun testResponseValidationPerRequestConfigFromFalseToTrue() = testWithEngine(MockEngine) {
        config {
            expectSuccess = false
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
        }

        test { client ->
            // expectSuccess default
            val response = client.get {}
            assertEquals(900, response.status.value)

            // expectSuccess overwritten
            try {
                client.get {
                    expectSuccess = true
                }
                fail("Should fail")
            } catch (cause: ResponseException) {
                assertEquals(900, cause.response.status.value)
                assertEquals("Awesome response", cause.response.body())
            }
        }
    }

    @Test
    fun testResponseValidationPerRequestConfigFromTrueToFalse() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
        }

        test { client ->
            // expectSuccess overwritten
            val response = client.get {
                expectSuccess = false
            }
            assertEquals(900, response.status.value)

            // expectSuccess default
            try {
                client.get {}
                fail("Should fail")
            } catch (cause: ResponseException) {
                assertEquals(900, cause.response.status.value)
                assertEquals("Awesome response", cause.response.body())
            }
        }
    }

    @Test
    fun testCustomResponseValidationRunsAfterDefault() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
            HttpResponseValidator {
                validateResponse {
                    throw IllegalStateException("Should not throw")
                }
            }
        }

        test { client ->
            try {
                client.get<String>()
                fail("Should fail")
            } catch (cause: ResponseException) {
                assertEquals(900, cause.response.status.value)
                assertEquals("Awesome response", cause.response.receive())
            }
        }
    }

    @Test
    fun testCustomResponseValidationWithoutDefault() = testWithEngine(MockEngine) {
        config {
            expectSuccess = false
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
            HttpResponseValidator {
                validateResponse {
                    throw IllegalStateException("My custom error")
                }
            }
        }

        test { client ->
            try {
                client.get<String>()
                fail("Should fail")
            } catch (cause: IllegalStateException) {
                assertEquals("My custom error", cause.message)
            }
        }
    }

    @Test
    fun testCustomResponseValidationWithoutDefaultPerRequestLevel() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            engine {
                addHandler {
                    val status = HttpStatusCode(900, "Awesome code")
                    respond("Awesome response", status)
                }
            }
            HttpResponseValidator {
                validateResponse {
                    throw IllegalStateException("My custom error")
                }
            }
        }

        test { client ->
            try {
                client.get<String> { expectSuccess = false }
                fail("Should fail")
            } catch (cause: IllegalStateException) {
                assertEquals("My custom error", cause.message)
            }
        }
    }
}

internal class CallValidatorTestException : Throwable()
