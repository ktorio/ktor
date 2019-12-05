/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
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
                client.get<HttpResponse>()
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")

            /**
             * Note: no way to join logger context => unpredictable logger output.
             */
        }
    }

    @Test
    fun testLogResponseWithException() = clientTest(MockEngine { request ->
        respondOk("Hello")
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
            client.get<HttpStatement>().execute {
                try {
                    it.receive<String>()
                } catch (_: CustomError) {
                    failed = true
                }
            }

            assertTrue(failed, "Exception is missing.")

            val dump = testLogger.dump()
            assertTrue(
                dump.contains("RESPONSE http://localhost/ failed with exception: CustomError: PARSE ERROR"),
                dump
            )
        }
    }
}

internal class CustomError(override val message: String) : Throwable()
