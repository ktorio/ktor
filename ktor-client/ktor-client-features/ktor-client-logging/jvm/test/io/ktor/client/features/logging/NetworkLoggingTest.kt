/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.logging.Logger
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.close
import kotlin.io.use
import kotlin.test.*

class NetworkLoggingTest : ClientLoader() {
    private val content = "Response data"
    private val serverPort = 8080

    @Ignore("Log structure is engine dependent")
    @Test
    fun testLoggingLevel(): Unit = clientTests {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.ALL
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.HEADERS
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.BODY
        )

        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/

        """.trimIndent(), HttpMethod.Get, "/", null, LogLevel.INFO
        )

        checkLog("", HttpMethod.Get, "/", null, LogLevel.NONE)
    }

    @Test
    @Ignore("Log structure is engine dependent")
    fun testLogPostBody(): Unit = clientTests {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=POST)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: text/plain; charset=UTF-8
BODY START
Response data
BODY END
RESPONSE: 201 Created
METHOD: HttpMethod(value=POST)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 1
BODY Content-Type: text/plain; charset=utf-8
BODY START
/
BODY END

        """.trimIndent(), HttpMethod.Post, "/", content, LogLevel.ALL
        )
    }

    @Ignore("Log structure is engine dependent")
    @Test
    fun logRedirectTest(): Unit = clientTests {
        checkLog(
            """
REQUEST: http://localhost:$serverPort/301
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
COMMON HEADERS
-> Accept: */*
CONTENT HEADERS
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 302 Found
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/301
COMMON HEADERS
-> Location: /
-> Content-Length: 0
BODY Content-Type: null
BODY START

BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
COMMON HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), HttpMethod.Get, "/301", null, LogLevel.ALL
        )
    }

    @Test
    fun customServerHeadersLoggingTest() = clientTests {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.HEADERS
            }
        }

        test { client ->
            client.get<String>("http://google.com")
        }
    }

    @Test
    fun customServerTest(): Unit = clientTests {
        config {
            Logging {
                level = LogLevel.ALL
                logger = Logger.Muted
            }
        }

        test { client ->
            client.request<HttpStatement> {
                method = HttpMethod.Get
                url("https://jigsaw.w3.org/HTTP/ChunkedScript")
            }.execute { response ->
                val responseBytes = ByteArray(65536)
                val body = response.content
                body.readFully(responseBytes)
            }
        }
    }

    private fun TestClientBuilder<*>.checkLog(
        expected: String,
        requestMethod: HttpMethod,
        path: String, body: String?, logLevel: LogLevel
    ) {
        val testLogger = TestLogger()

        config {
            install(Logging) {
                logger = testLogger
                level = logLevel
            }
        }

        test { client ->
            val response = client.request<HttpStatement> {
                method = requestMethod

                url {
                    encodedPath = "/logging/$path"
                    port = serverPort
                }

                body?.let { this@request.body = body }
            }.execute {
                it.readText()
                it
            }

            response.coroutineContext[Job]!!.join()
            assertEquals(expected.toLowerCase(), testLogger.dump().toLowerCase())
        }
    }
}
