/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.test.*

class WebSocketTest {

    @ExperimentalWebSocketExtensionApi
    @Test
    fun testAsDefault() {
        val feature = WebSockets(42, 16)
        val session = object : WebSocketSession {
            override var masking: Boolean = false
            override var maxFrameSize: Long = 0
            override val incoming: ReceiveChannel<Frame> = Channel()
            override val outgoing: SendChannel<Frame> = Channel()

            override val extensions: List<WebSocketExtension<*>>
                get() = TODO("Not yet implemented")

            override suspend fun send(frame: Frame) {
                TODO("Not yet implemented")
            }

            override suspend fun flush() {
                TODO("Not yet implemented")
            }

            override fun terminate() {
                TODO("Not yet implemented")
            }

            override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        }

        val defaultSession = feature.convertSessionToDefault(session)
        assertEquals(16, defaultSession.maxFrameSize)
    }
}
