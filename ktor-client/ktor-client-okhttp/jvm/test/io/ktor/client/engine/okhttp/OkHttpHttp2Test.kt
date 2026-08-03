/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OkHttpHttp2Test : Http2Test<OkHttpConfig>(OkHttp) {
    override fun OkHttpConfig.enableHttp2() {
        config { protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)) }
    }

    @Test
    fun testDuplexStreaming() = testClient {
        configureClient {
            engine { duplexStreamingEnabled = true }
        }

        test { client ->
            val inputChannel = ByteChannel(true)
            val response = client
                .preparePost("/echo/stream") {
                    setBody(inputChannel)
                }
                .execute {
                    val outputChannel = it.bodyAsChannel()
                    val buffer = StringBuilder()
                    (0..2).forEach { i ->
                        inputChannel.writeStringUtf8("client: $i\n")
                        inputChannel.flush()
                        outputChannel.readLineStrictTo(buffer)
                        buffer.append('\n')
                    }
                    buffer.toString()
                }
            assertEquals(
                """
                    server: client: 0
                    server: client: 1
                    server: client: 2
                """.trimIndent(),
                response.trim()
            )
        }
    }

    @Test
    fun testDuplexStreamingConnectionReleasedAfterRequest() = testClient {
        val connectionPool = ConnectionPool()
        configureClient {
            engine {
                duplexStreamingEnabled = true
                config { connectionPool(connectionPool) }
            }
        }

        test { client ->
            val inputChannel = ByteChannel(true)
            client
                .preparePost("/echo/stream") {
                    setBody(inputChannel)
                }
                .execute {
                    val outputChannel = it.bodyAsChannel()
                    val buffer = StringBuilder()
                    (0..2).forEach { i ->
                        inputChannel.writeStringUtf8("client: $i\n")
                        inputChannel.flush()
                        outputChannel.readLineStrictTo(buffer)
                        buffer.append('\n')
                    }
                    buffer.toString()
                }

            assertTrue(connectionPool.connectionCount() > 0, "Expected the request to use the injected pool")

            val released = withTimeoutOrNull(5.seconds) {
                while (connectionPool.idleConnectionCount() < connectionPool.connectionCount()) {
                    delay(50.milliseconds)
                }
            } != null
            assertTrue(released, "Connection should be released after request")
        }
    }

    @Test
    fun testDuplexStreamingExceptionPropagates() = testClient {
        configureClient {
            engine { duplexStreamingEnabled = true }
        }

        test { client ->
            val established = CompletableDeferred<Unit>()
            val failingBody = object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeStringUtf8("client: 0\n")
                    channel.flush()
                    established.await()
                    throw IllegalStateException("Client-side exception")
                }
            }

            assertFailsWith<ClosedByteChannelException> {
                client.preparePost("/echo/stream") {
                    setBody(failingBody)
                }.execute { response ->
                    val out = response.bodyAsChannel()
                    val first = out.readLineStrict()
                    assertEquals("server: client: 0", first)
                    established.complete(Unit)
                    out.readLineStrict()
                    fail("Expected duplex writer failure")
                }
            }.apply {
                assertEquals("Client-side exception", cause?.message)
            }
        }
    }
}
