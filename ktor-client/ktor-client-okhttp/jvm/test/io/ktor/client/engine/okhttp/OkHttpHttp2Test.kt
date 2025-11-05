/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class OkHttpHttp2Test : TestWithKtor() {

    override val server = embeddedServer(
        Netty,
        configure = {
            connector {
                port = serverPort
            }
            enableHttp2 = true
            enableH2c = true
        }
    ) {
        routing {
            post("/echo-stream") {
                val inputChannel = call.receiveChannel()

                call.respondBytesWriter(status = HttpStatusCode.OK) {
                    val outputChannel = this
                    while (true) {
                        val inputLine = inputChannel.readUTF8Line() ?: break
                        outputChannel.writeStringUtf8("server: $inputLine\n")
                        outputChannel.flush()
                    }
                }
            }
        }
    }

    @Test
    fun testDuplexStreaming() = testWithEngine(OkHttp) {
        config {
            engine {
                duplexStreamingEnabled = true
                config {
                    protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                }
            }
        }

        test { client ->
            val inputChannel = ByteChannel(true)
            val response = client
                .preparePost("$testUrl/echo-stream") {
                    setBody(inputChannel)
                }
                .execute {
                    val outputChannel = it.bodyAsChannel()
                    var acc = ""
                    (0..2).forEach {
                        inputChannel.writeStringUtf8("client: $it\n")
                        inputChannel.flush()
                        acc += outputChannel.readUTF8Line()
                        acc += "\n"
                    }
                    acc
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
    fun testDuplexStreamingExceptionPropagates() = testWithEngine(OkHttp) {
        config {
            engine {
                duplexStreamingEnabled = true
                config {
                    protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                }
            }
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
                client.preparePost("$testUrl/echo-stream") {
                    setBody(failingBody)
                }.execute { response ->
                    val out = response.bodyAsChannel()
                    val first = out.readUTF8Line()
                    assertEquals("server: client: 0", first)
                    established.complete(Unit)
                    out.readUTF8Line()
                    fail("Expected duplex writer failure")
                }
            }.apply {
                assertEquals("Client-side exception", cause?.message)
            }
        }
    }
}
