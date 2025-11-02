/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import okhttp3.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
