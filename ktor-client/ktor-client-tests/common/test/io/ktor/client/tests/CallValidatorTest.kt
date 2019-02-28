package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CallValidatorTest {
    @Test
    fun testAllExceptionHandlers() = clientTest(MockEngine) {
        var firstHandler = 0
        var secondHandler = 0

        config {
            engine {
                addHandler { it.responseOk() }
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
    fun testExceptionFromEngine() = clientTest(MockEngine) {
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
                client.call()
            } catch (_: CallValidatorTestException) {
            }

            assertEquals(1, handleTriggered)
        }
    }

    @Test
    fun testExceptionFromReceivePipeline() = clientTest(MockEngine) {
        var handleTriggered = false
        config {
            engine {
                addHandler { response -> response.responseOk() }
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
    fun testMergeMultipleConfigs() = clientTest(MockEngine) {
        var firstHandler = 0
        var secondHandler = 0

        config {
            engine {
                addHandler { it.responseOk() }
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
    fun testResponseValidation() = clientTest(MockEngine) {
        var validator = 0
        config {
            engine {
                addHandler {
                    val status = HttpStatusCode(42, "Awesome code")
                    it.response("Awesome response", status)
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
