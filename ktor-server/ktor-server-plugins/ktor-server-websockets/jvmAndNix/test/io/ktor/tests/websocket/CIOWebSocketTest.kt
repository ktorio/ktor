/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.*
import kotlin.test.*

@InternalAPI
class CIOWebSocketTest : WebSocketEngineSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableSsl = false
        enableHttp2 = false
    }

    override fun plugins(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        super.plugins(application, routingConfigurer)
    }

    data class Data(val s: Int)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializer(forClass = Data::class)
    object DataSerializer

    @Test
    fun testWebSocketCustomSerializer() = runTest {
        createAndStartServer {
            webSocket("/") {
                val data = receiveDeserialized(DataSerializer)
                sendSerialized(data, DataSerializer)
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            val data = Data(42)
            output.writeFrame(Frame.Text(Json.encodeToString(DataSerializer, data)), masking = false)
            output.flush()

            val frame = input.readFrame(Long.MAX_VALUE, 0)
            assertIs<Frame.Text>(frame)
            val incomingData = Json.decodeFromString(DataSerializer, frame.readText())
            assertEquals(data, incomingData)

            output.writeFrame(Frame.Close(), false)
            output.flush()
            assertCloseFrame()
        }
    }
}
