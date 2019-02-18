package io.ktor.client.features.logging

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import kotlin.test.*


class CommonLoggingTest {

    @Test
    fun testLogRequestWithException() = clientTest(MockEngine {
        throw CustomError("BAD REQUEST")
    }) {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                client.get<String>()
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")

            assertEquals(
                """
REQUEST: http://localhost/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
REQUEST http://localhost/ failed with exception: io.ktor.client.features.logging.CustomError: BAD REQUEST

                """.trimIndent(),
                testLogger.dump()
            )
        }
    }

    @Test
    fun testLogResponseWithException() = clientTest(MockEngine {
        responseOk("Hello")
    }) {
        val testLogger = TestLogger()

        config {
            install("BadInterceptor") {
                responsePipeline.intercept(HttpResponsePipeline.Parse) {
                    throw CustomError("PARSE ERROR")
                }
            }

            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                client.get<String>()
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")

            assertEquals(
                """
REQUEST: http://localhost/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
RESPONSE http://localhost/ failed with exception: io.ktor.client.features.logging.CustomError: PARSE ERROR

                """.trimIndent(),
                testLogger.dump()
            )
        }
    }
}

internal class CustomError(override val message: String): Throwable()
