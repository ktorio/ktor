/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
            assertTrue(message = "More ${loggedLines.size - currentLine} logs present, expected none") { currentLine >= loggedLines.size }
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




