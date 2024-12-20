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
    class LogRecorder : Logger {
        private val loggedLines = mutableListOf<String>()
        private var currentLine = 0
        override fun log(message: String) {
            loggedLines.add(message)
        }

        fun assertLogEqual(msg: String): LogRecorder {
            assertTrue(message = "No more logs to check") { currentLine < loggedLines.size }
            assertEquals(msg, loggedLines[currentLine])
            currentLine++
            return this
        }

        fun assertLogMatch(regex: Regex): LogRecorder {
            assertTrue(message = "No more logs to check") { currentLine < loggedLines.size }
            assertTrue(message = "Regex '$regex' doesn't match '${loggedLines[currentLine]}'") {
                regex.matches(
                    loggedLines[currentLine]
                )
            }
            currentLine++
            return this
        }

        fun assertNoMoreLogs(): LogRecorder {
            assertTrue(message = "There are ${loggedLines.size - currentLine} more logs, expected none") { currentLine >= loggedLines.size }
            return this
        }
    }

    private lateinit var log: LogRecorder

    @BeforeEach
    fun setup() {
        log = LogRecorder()
    }

    @Test
    fun noLoggingWhenLevelNone() = testWithLevel(LogLevel.NONE, handle = { respondWithLength() }) { client ->
        client.get("/")
        log.assertNoMoreLogs()
    }

    @Test
    fun basicGet() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPost() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/") {
            setBody("hello")
        }

        log.assertLogEqual("--> POST / (5-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGet404() = testWithLevel(LogLevel.INFO, handle = { respondWithLength("", HttpStatusCode.NotFound) }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 404 Not Found / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetNonRoot() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.get("/some/resource")

        log.assertLogEqual("--> GET /some/resource")
            .assertLogMatch(Regex("""<-- 200 OK /some/resource \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetQuery() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.get("/?a=1&b=2&c=3")

        log.assertLogEqual("--> GET /?a=1&b=2&c=3")
            .assertLogMatch(Regex("""<-- 200 OK /\?a=1&b=2&c=3 \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetNonEmptyBody() = testWithLevel(LogLevel.INFO, handle = { respondWithLength("hello") }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 5-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostNoBody() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/")

        log.assertLogEqual("--> POST / (0-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostUpgradeProtocol() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
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
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()

    }

    @Test
    fun basicPostReadChannel() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom() = ByteReadChannel("hello world")
            })
        }

        log.assertLogEqual("--> POST / (unknown-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostReadChannelWithContentLength() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentLength: Long?
                    get() = 11
                override fun readFrom() = ByteReadChannel("hello world")
            })
        }

        log.assertLogEqual("--> POST / (11-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostConsumedRequestBody() = testWithLevel(LogLevel.INFO, handle = {
        respondWithLength(it.body.toByteReadPacket().readByteArray())
    }) { client ->
        val response = client.post("/") {
            setBody(ByteReadChannel("hello"))
        }

        assertEquals("hello", response.bodyAsText())

        log.assertLogEqual("--> POST / (unknown-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 5-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostWriteChannel() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8("hello world")
                }
            })
        }

        log.assertLogEqual("--> POST / (unknown-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicPostWriteChannelWithContentLength() = testWithLevel(LogLevel.INFO, handle = { respondWithLength() }) { client ->
        client.post("/") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8("hello world")
                }
                override val contentLength: Long?
                    get() = 11
            })
        }

        log.assertLogEqual("--> POST / (11-byte body)")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicGetWithResponseContentLength() = testWithLevel(LogLevel.INFO, handle = {
        respond("", headers = Headers.build { append(HttpHeaders.ContentLength, "10") })
    }) { client ->
        client.prepareGet("/").execute { response ->
            log.assertLogEqual("--> GET /")
                .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 10-byte body\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun basicGzippedBody()  = testWithLevel(LogLevel.INFO, handle = {
        val channel = GZipEncoder.encode(ByteReadChannel("a".repeat(1024)))
        respond(channel, headers = Headers.build {
            append(HttpHeaders.ContentEncoding, "gzip")
            append(HttpHeaders.ContentLength, "29")
        })
    }) { client ->
        client.prepareGet("/").execute { response ->
            log.assertLogEqual("--> GET /")
                .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, 29-byte body\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun basicGzippedBodyContentEncoding() = runTest {
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
                    respond(channel, headers = Headers.build {
                        append(HttpHeaders.ContentEncoding, "gzip")
                        append(HttpHeaders.ContentLength, "29")
                    })
                }
            }
        }.use { client ->
            val response = client.get("/")
            assertEquals("a".repeat(1024), response.bodyAsText())

            log.assertLogEqual("--> GET /")
                .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun headersGet() = testWithLevel(LogLevel.HEADERS, handle = { respondWithLength() }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersPost() = testWithLevel(LogLevel.HEADERS, handle = { respondWithLength() }) { client ->
        client.post("/post") {
            setBody(TextContent(text = "hello", contentType = ContentType.Text.Plain))
        }

        log.assertLogEqual("--> POST /post")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("Content-Length: 5")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END POST")
            .assertLogMatch(Regex("""<-- 200 OK /post \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersNoLength() = testWithLevel(LogLevel.HEADERS, handle = { respondWithLength() }) { client ->
        client.post("/post") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8("test")
                }
            })
        }

        log.assertLogEqual("--> POST /post")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END POST")
            .assertLogMatch(Regex("""<-- 200 OK /post \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun customHeaders() = testWithLevel(LogLevel.HEADERS, handle = {
        respondWithLength("hello", headers = Headers.build {
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
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Custom-Response: value")
            .assertLogEqual("Content-Length: 5")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersResponseBody() = testWithLevel(LogLevel.HEADERS, handle = {
        respondWithLength("test", contentType = ContentType.Text.Html)
    }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 4")
            .assertLogEqual("Content-Type: text/html")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun noBodiesSizesWhenHasContentLengths() = testWithLevel(LogLevel.HEADERS, handle = {
        respondWithLength("bye")
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
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 3")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersGzippedResponseBody() = testWithLevel(LogLevel.HEADERS, handle = {
        val content = "a".repeat(1024)
        val channel = GZipEncoder.encode(ByteReadChannel(content))
        respond(channel, headers = Headers.build {
            append(HttpHeaders.ContentEncoding, "gzip")
            append(HttpHeaders.ContentLength, "29")
        })
    }) { client ->
        client.get("/")

        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Encoding: gzip")
            .assertLogEqual("Content-Length: 29")
            .assertLogEqual("<-- END HTTP")
            .assertNoMoreLogs()
    }

    @Test
    fun headersGzippedResponseBodyContentEncoding() = runTest {
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
                .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, unknown-byte body\)"""))
                .assertLogEqual("<-- END HTTP")
                .assertNoMoreLogs()
        }
    }

    @Test
    fun bodyGzippedResponseBody() = testWithLevel(LogLevel.BODY, handle = {
        val channel = GZipEncoder.encode(ByteReadChannel("response".repeat(1024)))
        respond(channel, headers = Headers.build {
            append(HttpHeaders.ContentEncoding, "gzip")
            append(HttpHeaders.ContentLength, "55")
        })
    }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Encoding: gzip")
            .assertLogEqual("Content-Length: 55")
            .assertLogEqual("")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, binary 55-byte body omitted\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun bodyResponseBodyBrEncoded() = testWithLevel(LogLevel.BODY, handle = {
        respond(byteArrayOf(0xC3.toByte(), 0x28), headers = Headers.build {
            append(HttpHeaders.ContentEncoding, "br")
            append(HttpHeaders.ContentLength, "2")
        })
    }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Encoding: br")
            .assertLogEqual("Content-Length: 2")
            .assertLogEqual("")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, binary 2-byte body omitted\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun bodyGzippedResponseBodyContentEncoding() = runTest {
        HttpClient(MockEngine) {
            install(Logging) {
                level = LogLevel.BODY
                logger = log
                standardFormat = true
            }
            install(ContentEncoding) { gzip() }

            engine {
                addHandler {
                    val channel = GZipEncoder.encode(ByteReadChannel("response".repeat(1024)))
                    respond(channel, headers = Headers.build { append(HttpHeaders.ContentEncoding, "gzip") })
                }
            }
        }.use { client ->
            client.get("/")

            log.assertLogEqual("--> GET /")
                .assertLogEqual("Accept-Encoding: gzip")
                .assertLogEqual("Accept-Charset: UTF-8")
                .assertLogEqual("Accept: */*")
                .assertLogEqual("--> END GET")
                .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
                .assertLogEqual("")
                .assertLogEqual("response".repeat(1024))
                .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 8192-byte body\)"""))
                .assertNoMoreLogs()
        }
    }

    @Test
    fun bodyGet() = testWithLevel(LogLevel.BODY, handle = { respondWithLength() }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun bodyGet204() = testWithLevel(LogLevel.BODY, handle = {
        respond("", status = HttpStatusCode.NoContent, headers = Headers.build {
            append(HttpHeaders.ContentLength, "0")
        })
    }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 204 No Content / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun bodyGet205() = testWithLevel(LogLevel.BODY, handle = {
        respond("", status = HttpStatusCode.ResetContent, headers = Headers.build {
            append(HttpHeaders.ContentLength, "0")
        })
    }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 205 Reset Content / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 0")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 0-byte body\)"""))
            .assertNoMoreLogs()
    }

//    @Test
//    fun bodyPost() = testWithLevel(LogLevel.BODY, handle = { respondWithLength() }) { client ->
//        client.post("/") {
//            setBody("test")
//        }
//        log.assertLogEqual("--> POST /")
//            .assertLogEqual("Content-Type: text/plain; charset=UTF-8")
//            .assertLogEqual("Content-Length: 4")
//            .assertLogEqual("Accept-Charset: UTF-8")
//            .assertLogEqual("Accept: */*")
//            .assertLogEqual("")
//            .assertLogEqual("test")
//            .assertLogEqual("--> END POST (4-byte body)")
//            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
//            .assertLogEqual("Content-Length: 0")
//            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 0-byte body\)"""))
//            .assertNoMoreLogs()
//    }

    @Test
    fun bodyGetWithResponseBody() = testWithLevel(LogLevel.BODY, handle = { respondWithLength("hello!") }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Content-Length: 6")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("")
            .assertLogEqual("hello!")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 6-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun bodyResponseBodyChunked() = testWithLevel(LogLevel.BODY, handle = { respondChunked(ByteReadChannel("hello!")) }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogEqual("Accept-Charset: UTF-8")
            .assertLogEqual("Accept: */*")
            .assertLogEqual("--> END GET")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms\)"""))
            .assertLogEqual("Transfer-Encoding: chunked")
            .assertLogEqual("Content-Type: text/plain")
            .assertLogEqual("")
            .assertLogEqual("hello!")
            .assertLogMatch(Regex("""<-- END HTTP \(\d+ms, 6-byte body\)"""))
            .assertNoMoreLogs()
    }

    @Test
    fun basicChunkedResponseBody() = testWithLevel(LogLevel.INFO, handle = {
        respond(ByteReadChannel("test"), headers = Headers.build {
            append(HttpHeaders.TransferEncoding, "chunked")
        })
    }) { client ->
        client.get("/")
        log.assertLogEqual("--> GET /")
            .assertLogMatch(Regex("""<-- 200 OK / \(\d+ms, unknown-byte body\)"""))
            .assertNoMoreLogs()
    }

    private fun MockRequestHandleScope.respondWithLength(): HttpResponseData {
        return respond("", headers = Headers.build {
            append("Content-Length", "0")
        })
    }

    private fun MockRequestHandleScope.respondChunked(body: ByteReadChannel, status: HttpStatusCode = HttpStatusCode.OK, contentType: ContentType = ContentType.Text.Plain, headers: Headers = Headers.Empty): HttpResponseData {
        return respond(body, headers = Headers.build {
            appendAll(headers)
            append("Transfer-Encoding", "chunked")
            set("Content-Type", contentType.toString())
        }, status = status)
    }

    private fun MockRequestHandleScope.respondWithLength(body: String, status: HttpStatusCode = HttpStatusCode.OK, contentType: ContentType = ContentType.Text.Plain, headers: Headers = Headers.Empty): HttpResponseData {
        return respond(ByteReadChannel(body), headers = Headers.build {
            appendAll(headers)
            append("Content-Length", body.length.toString())
            set("Content-Type", contentType.toString())
        }, status = status)
    }

    private fun MockRequestHandleScope.respondWithLength(body: ByteArray, status: HttpStatusCode = HttpStatusCode.OK, contentType: ContentType = ContentType.Text.Plain, headers: Headers = Headers.Empty): HttpResponseData {
        return respond(ByteReadChannel(body), headers = Headers.build {
            appendAll(headers)
            append("Content-Length", body.size.toString())
            set("Content-Type", contentType.toString())
        }, status = status)
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




