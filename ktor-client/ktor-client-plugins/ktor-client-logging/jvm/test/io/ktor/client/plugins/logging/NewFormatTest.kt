/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.GZipEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.BeforeEach
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewFormatTest {
    class MemLogger : Logger {
        private val loggedLines = mutableListOf<String>()
        private var currentLine = 0
        override fun log(message: String) {
            loggedLines.add(message)
        }

        fun assertLogEqual(msg: String): MemLogger {
            assertTrue(message = "No more logs to check, got some") { currentLine < loggedLines.size }
            assertEquals(msg, loggedLines[currentLine])
            currentLine++
            return this
        }

        fun assertLogMatch(regex: Regex): MemLogger {
            assertTrue(message = "No more logs to check, got some") { currentLine < loggedLines.size }
            assertTrue(message = "Regex '$regex' doesn't match '${loggedLines[currentLine]}'") {
                regex.matches(
                    loggedLines[currentLine]
                )
            }
            currentLine++
            return this
        }

        fun assertNoMoreLogs(): MemLogger {
            assertTrue(message = "There are ${loggedLines.size - currentLine} more logs, expected none") { currentLine >= loggedLines.size }
            return this
        }
    }

    private lateinit var log: MemLogger

    @BeforeEach
    fun setup() {
        log = MemLogger()
    }

    @Test
    fun basicGet() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPost() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.post("/") {
            setBody("hello")
        }

        log.assertLogEqual("--> POST / (5-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()

    }

    @Test
    fun basicGet404() = testWithLevel(LogLevel.INFO, handle = { respond("", HttpStatusCode.NotFound) }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 404 Not Found / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetNonRoot() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.get("/some/resource")

        log.assertLogEqual("--> GET /some/resource")
            .assertLogMatch(Regex("""<-- 200 OK /some/resource HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetQuery() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.get("/?a=1&b=2&c=3")

        log.assertLogEqual("--> GET /?a=1&b=2&c=3")
            .assertLogMatch(Regex("""<-- 200 OK /\?a=1&b=2&c=3 HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetNonEmptyBody() = testWithLevel(LogLevel.INFO, handle = {
        respond("hello", headers = Headers.build {
            append(HttpHeaders.ContentLength, "5")
        })
    }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 5-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostNoBody() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.post("/")

        log.assertLogEqual("--> POST / (0-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()

    }

    @Test
    fun basicPostUpgradeProtocol() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.ProtocolUpgrade() {
                override suspend fun upgrade(
                    input: ByteReadChannel,
                    output: ByteWriteChannel,
                    engineContext: CoroutineContext,
                    userContext: CoroutineContext
                ): Job {
                    output.flushAndClose()
                    return Job()
                }
            })
        }

        log.assertLogEqual("--> POST / (0-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()

    }

    @Test
    fun basicPostReadChannel() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom() = ByteReadChannel("hello world")
            })
        }

        log.assertLogEqual("--> POST / (11-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostConsumedRequestBody() = testWithLevel(LogLevel.INFO, handle = {
        respond(it.body.toByteReadPacket().readByteArray())
    }) { client ->
        val response = client.post("/") {
            setBody(ByteReadChannel("hello"))
        }

        assertEquals("hello", response.bodyAsText())

        log.assertLogEqual("--> POST / (5-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 5-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostWriteChannel() = testWithLevel(LogLevel.INFO, handle = { respondOk() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8("hello world")
                }
            })
        }

        log.assertLogEqual("--> POST / (11-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetWithResponseContentLength() = testWithLevel(LogLevel.INFO, handle = {
        respond("", headers = Headers.build { append(HttpHeaders.ContentLength, "10") })
    }) { client ->
        client.prepareGet("/").execute { response ->
            log.assertLogEqual("--> GET /")
                .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 10-byte body\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun basicPostWithGzip() = runTest {
        HttpClient(MockEngine) {
            install(Logging) {
                level = LogLevel.INFO
                logger = log
                standardFormat = true
            }
            install(ContentEncoding) { gzip() }

            engine {
                addHandler {
                    val channel = GZipEncoder.encode(ByteReadChannel("a".repeat(1024)))
                    respond(channel, headers = Headers.build { append(HttpHeaders.ContentEncoding, "gzip") })
                }
            }
        }.use { client ->
            client.post("/")

            log.assertLogEqual("--> POST / (0-byte body)")
                .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 1024-byte body\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun headersGet() = testWithLevel(LogLevel.HEADERS, handle = { respondOk() }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersPost() = testWithLevel(LogLevel.HEADERS, handle = { respondOk() }) { client ->
        client.post("/post") {
            setBody(TextContent(text = "hello", contentType = ContentType.Text.Plain))
        }

        log.assertLogEqual("--> POST /post")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("Content-Length: 5")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END POST")
            .assertLogMatch(Regex("""<-- 200 OK /post HTTP/1.1 \(\d+ms, 0-byte body\)"""))
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun customHeaders() = testWithLevel(LogLevel.HEADERS, handle = {
        respond("hello", headers = Headers.build {
            append("Custom-Response", "value")
        })
    }) { client ->
        client.get("/") {
            setBody(TextContent(text = "hello", contentType = ContentType.Text.Plain))
            header("Custom-Request", "value")
        }

        log.assertLogEqual("--> GET /")
            .assertLogEqual("Custom-Request: value")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 5-byte body\)"""))
            .assertLogEqual("Custom-Response: value")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun noBodiesSizesWhenHasContentLengths() = testWithLevel(LogLevel.HEADERS, handle = {
        respond("bye", headers = Headers.build {
            append("Content-Length", "3")
        })
    }) { client ->
        client.post("/") {
            setBody(TextContent(text = "hello", contentType = ContentType.Text.Plain))
        }

        log.assertLogEqual("--> POST /")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("Content-Length: 5")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END POST")
            .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 3")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersPostWithGzip() = runTest {
        HttpClient(MockEngine) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = log
                standardFormat = true
            }
            install(ContentEncoding) { gzip() }

            engine {
                addHandler {
                    val channel = GZipEncoder.encode(ByteReadChannel("a".repeat(1024)))
                    respond(channel, headers = Headers.build { append(HttpHeaders.ContentEncoding, "gzip") })
                }
            }
        }.use { client ->
            client.post("/")

            log.assertLogEqual("--> POST /")
                .assertLogEqual("Accept-Encoding: gzip")
                .assertLogEqual("Accept-Charset: UTF-8")
                .assertLogEqual("Accept: */*")
                .assertLogEqual("--> END POST")
                .assertLogMatch(Regex("""<-- 200 OK / HTTP/1.1 \(\d+ms, 1024-byte body\)"""))
                .assertLogEqual("<-- END HTTP")
                .assertNoMoreLogs()
        }
    }

    @Test
    fun noLoggingWhenLevelNone() = testWithLevel(LogLevel.NONE, handle = { respondOk() }) { client ->
        client.get("/")
        log.assertNoMoreLogs()
    }

    private fun testWithLevel(lvl: LogLevel, handle: MockRequestHandler, test: suspend (HttpClient) -> Unit) = runTest {
        HttpClient(MockEngine) {
            install(Logging) {
                level = lvl
                logger = log
                standardFormat = true
            }

            engine {
                addHandler(handle)
            }
        }.use { client ->
            test(client)
        }
    }
}




