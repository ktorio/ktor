/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

// Test checks if headers is flushing immediately before writing body to the channel
class EngineHeaderFlushingTest : TestWithKtor() {
    override val server = embeddedServer(Netty, serverPort) {
        routing {
            route("/timed") {
                post {
                    val byteStream = ByteChannel(autoFlush = true)

                    launch(Dispatchers.Unconfined) {
                        byteStream.writePacket(call.request.receiveChannel().readRemaining())
                        byteStream.close(null)
                    }
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        override val status: HttpStatusCode = HttpStatusCode.OK
                        override val contentType: ContentType = ContentType.Text.Plain
                        override val headers: Headers = Headers.Empty
                        override fun readFrom() = byteStream
                    })
                }
            }
        }
    }

    @Test
    fun testHeadersFlush() {
        val client = HttpClient {
            install(HttpTimeout) {
                socketTimeoutMillis = 10000
            }
        }

        runBlocking {
            val requestBody = ByteChannel()

            client.preparePost("$testUrl/timed") {
                setBody(requestBody)
            }.execute { httpResponse ->
                assertEquals(httpResponse.status, HttpStatusCode.OK)
                assertEquals(httpResponse.contentType(), ContentType.Text.Plain)

                val channel: ByteReadChannel = httpResponse.body()
                assertEquals(0, channel.availableForRead)

                requestBody.writeFully(Charsets.UTF_8.encode("hello"))
                requestBody.close(null)

                assertEquals(channel.readRemaining().readText(), "hello")
            }
        }
    }
}
