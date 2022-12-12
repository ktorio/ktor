/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class WebSocketReaderTest {
    @Test
    fun testFailFragmentedControl() {
        val serializer = Serializer()
        val channel = ByteChannel(true)
        runBlocking {
            val reader = WebSocketReader(channel, coroutineContext, 1000)
            val frame = Frame.Ping(ByteArray(126))
            serializer.enqueue(frame)

            while (serializer.hasOutstandingBytes) {
                channel.write {
                    serializer.serialize(it)
                }
            }
            assertFailsWith<ProtocolViolationException> {
                reader.incoming.receive()
            }
        }
    }
}
