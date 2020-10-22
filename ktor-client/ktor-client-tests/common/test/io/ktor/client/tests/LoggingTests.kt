/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class LoggingTest : ClientLoader() {
    private val content = "Response data"
    private val serverPort = 8080

    @Test
    fun testDownloadWithNoneLogLevel() = clientTests {
        val testLogger = TestLogger()
        config {
            install(Logging) {
                level = LogLevel.NONE
                logger = testLogger
            }
        }

        test { client ->
            val size = 4 * 1024 * 1024
            client.get<HttpStatement>("$TEST_SERVER/bytes?size=$size").execute {
                assertEquals(size, it.readBytes().size)
            }
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLoggingLevelBody() = clientTests(listOf("native:CIO")) {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging/",
            "METHOD: HttpMethod(value=GET)",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging/",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "home page",
            "BODY END"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.BODY)
    }

    @Test
    fun testLogLevelAll() = clientTests(listOf("native:CIO")) {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging/",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging/",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "???-> Connection: close",
            "-> Content-Length: 9",
            "-> Content-Type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "home page",
            "BODY END"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.ALL)
    }

    @Test
    fun testLogLevelHeaders() = clientTests {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging/",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging/",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> Connection: keep-alive",
            "-> Content-Length: 9",
            "-> Content-Type: text/plain; charset=UTF-8"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.HEADERS)
    }

    @Test
    fun testLogLevelInfo() = clientTests {
        val logger = TestLogger(
            "REQUEST: http://localhost:8080/logging/",
            "METHOD: HttpMethod(value=GET)",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://localhost:8080/logging/"
        )
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.INFO)
    }

    @Test
    fun testLogLevelNone() = clientTests {
        val logger = TestLogger()
        checkLog(logger, HttpMethod.Get, "", null, LogLevel.NONE)
    }

    @Test
    fun testLogPostBody() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://localhost:8080/logging/",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 13",
            "-> Content-Length: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            content,
            "BODY END",
            "RESPONSE: 201 Created",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://localhost:8080/logging/",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> content-length: 1",
            "-> content-type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "/",
            "BODY END"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request<HttpStatement> {
                method = HttpMethod.Post

                url {
                    encodedPath = "/logging/"
                    port = serverPort
                }

                body = content
            }.execute {
                it.readText()
                it
            }

            response.coroutineContext[Job]!!.join()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLogPostMalformedUtf8Body() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://localhost:8080/logging/non-utf",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 2",
            "-> Content-Length: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "[request body omitted]",
            "BODY END",
            "RESPONSE: 201 Created",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://localhost:8080/logging/non-utf",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> content-length: 2",
            "-> content-type: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "[response body omitted]",
            "BODY END"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request<HttpStatement> {
                method = HttpMethod.Post

                url {
                    encodedPath = "/logging/non-utf"
                    port = serverPort
                }

                body = byteArrayOf(-77, 111)
            }.execute {
                it.readBytes()
                it
            }

            response.coroutineContext[Job]!!.join()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testLogRedirect() = clientTests(listOf("js", "Curl", "CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/logging/301",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 302 Found",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://127.0.0.1:8080/logging/301",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "???-> Connection: close",
            "-> Content-Length: 0",
            "-> Location: /logging",
            "BODY Content-Type: null",
            "BODY START",
            "!!! body can be cancelled or printed",
            "BODY END",
            "REQUEST: http://127.0.0.1:8080/logging",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "BODY Content-Type: null",
            "BODY START",
            "",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://127.0.0.1:8080/logging",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "-> Content-Length: 9",
            "-> Content-Type: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "home page",
            "BODY END"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            testLogger.reset()

            val response = client.request<HttpStatement> {
                method = HttpMethod.Get
                url.takeFrom("$TEST_SERVER/logging/301")
            }.execute {
                it.readText()
                it
            }

            response.coroutineContext[Job]!!.join()
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testCustomServerHeadersLogging() = clientTests(listOf("Curl", "iOS", "Js")) {
        val testLogger = TestLogger(
            "REQUEST: http://google.com/",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "RESPONSE: 301 Moved Permanently",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://google.com/",
            "COMMON HEADERS",
            "???-> Cache-Control: public, max-age=2592000",
            "???-> Content-Length: 219",
            "-> Content-Type: text/html; charset=UTF-8",
            "!!!-> Date: Mon, 23 Mar 2020 13:18:51 GMT",
            "!!!-> Expires: Wed, 22 Apr 2020 13:18:51 GMT",
            "-> Location: http://www.google.com/",
            "-> Server: gws",
            "-> X-Frame-Options: SAMEORIGIN",
            "-> X-XSS-Protection: 0",
            "REQUEST: http://www.google.com/",
            "METHOD: HttpMethod(value=GET)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 0",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=GET)",
            "FROM: http://www.google.com/",
            "COMMON HEADERS",
            "???-> Accept-Ranges: none",
            "???-> Cache-Control: private, max-age=0",
            "???-> Content-Encoding: gzip",
            "???-> Content-Length: 6442",
            "-> Content-Type: text/html; charset=ISO-8859-1",
            "!!!-> Date: Mon, 23 Mar 2020 07:36:35 GMT",
            "-> Expires: -1",
            "-> P3P: CP=\"This is not a P3P policy! See g.co/p3phelp for more info.\"",
            "-> Server: gws",
            "!!!-> Set-Cookie: 1P_JAR=2020-03-23-07; expires=Wed, 22-Apr-2020 07:36:35 GMT; path=/; domain=.google.com; Secure; NID=200=iPPuoTmF9xZOGdMHGAYjAwwyxiYdIG_OQ4xtq4Xtm8vAnz5zsyM_ciT4sySPdEVNEAg1fIn2rhh7roSbzG4Dv9RoQEqJmovWTmFWK72fYd8EMozgZ_93BetHhJgzAfW9r8wduUg-xCDnonFSMST6KjpxkBQSRQ88cdmnX5f9nO4; expires=Tue, 22-Sep-2020 07:36:35 GMT; path=/; domain=.google.com; HttpOnly",
            "???-> Transfer-Encoding: chunked",
            "???-> Vary: Accept-Encoding",
            "-> X-Frame-Options: SAMEORIGIN",
            "-> X-XSS-Protection: 0"
        )

        config {
            install(Logging) {
                logger = testLogger
                level = LogLevel.HEADERS
            }
        }

        test { client ->
            client.get<String>("http://google.com")
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testRequestAndResponseBody() = clientTests(listOf("native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 4",
            "-> Content-Length: text/plain; charset=UTF-8",
            "BODY Content-Type: text/plain; charset=UTF-8",
            "BODY START",
            "test",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://127.0.0.1:8080/content/echo",
            "COMMON HEADERS",
            "???-> Connection: close",
            "???-> connection: keep-alive",
            "-> content-length: 4",
            "BODY Content-Type: null",
            "BODY START",
            "test",
            "BODY END"
        )

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request<ByteReadChannel> {
                method = HttpMethod.Post
                body = "test"
                url("$TEST_SERVER/content/echo")
            }
            assertNotNull(response)
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testCustomServer() = clientTests(listOf("iOS", "native:CIO")) {
        config {
            Logging {
                level = LogLevel.ALL
                logger = Logger.EMPTY
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

    @Test
    fun testRequestContentTypeInLog() = clientTests(listOf("iOS", "native:CIO")) {
        val testLogger = TestLogger(
            "REQUEST: http://127.0.0.1:8080/content/echo",
            "METHOD: HttpMethod(value=POST)",
            "COMMON HEADERS",
            "-> Accept: */*",
            "-> Accept-Charset: UTF-8",
            "CONTENT HEADERS",
            "-> Content-Length: 4",
            "-> Content-Length: application/octet-stream",
            "BODY Content-Type: application/octet-stream",
            "BODY START",
            "test",
            "BODY END",
            "RESPONSE: 200 OK",
            "METHOD: HttpMethod(value=POST)",
            "FROM: http://127.0.0.1:8080/content/echo",
            "COMMON HEADERS",
            "???-> Connection: keep-alive",
            "-> Content-Length: 4",
            "BODY Content-Type: null",
            "BODY START",
            "test",
            "BODY END"
        )

        config {
            Logging {
                logger = testLogger
                level = LogLevel.ALL
            }
        }

        test { client ->
            val response = client.request<ByteReadChannel> {
                method = HttpMethod.Post
                body = "test"
                contentType(ContentType.Application.OctetStream)
                url("$TEST_SERVER/content/echo")
            }

            assertNotNull(response)
        }

        after {
            testLogger.verify()
        }
    }

    private fun TestClientBuilder<*>.checkLog(
        testLogger: TestLogger,
        requestMethod: HttpMethod,
        path: String, body: String?, logLevel: LogLevel
    ) {
        config {
            install(Logging) {
                logger = testLogger
                level = logLevel
            }
        }

        test { client ->
            client.request<String> {
                method = requestMethod

                url {
                    encodedPath = "/logging/$path"
                    port = serverPort
                }

                body?.let { this@request.body = body }
            }
        }

        after {
            testLogger.verify()
        }
    }
}

