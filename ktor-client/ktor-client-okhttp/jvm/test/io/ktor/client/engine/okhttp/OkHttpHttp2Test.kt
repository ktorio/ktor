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
import okhttp3.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

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
