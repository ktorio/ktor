/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlin.test.*

class WebSocketWithContentNegotiationTest {

    @Test
    fun test(): Unit = withTestApplication {
        application.install(WebSockets)
        application.install(ContentNegotiation) {
            val converter = object : ContentConverter {
                override suspend fun convertForSend(
                    context: PipelineContext<Any, ApplicationCall>,
                    contentType: ContentType,
                    value: Any
                ): Any? = fail("convertForSend shouldn't be invoked")

                override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
                    fail("convertForReceive shouldn't be invoked")
                }
            }

            register(ContentType.Any, converter)
        }

        application.routing {
            webSocket("/") {
                outgoing.send(Frame.Text("OK"))
            }
        }

        handleWebSocketConversation("/", {}) { incoming, _ ->
            incoming.receive()
        }
    }
}
