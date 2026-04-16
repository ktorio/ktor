/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.server.netty.http.*
import io.ktor.utils.io.*
import io.netty.buffer.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.channels.*

internal suspend fun ReceiveChannel<Http2DataFrame>.http2frameLoop(bc: ByteWriteChannel) {
    try {
        while (true) {
            val message = receive()
            val content = message.content() ?: Unpooled.EMPTY_BUFFER

            try {
                transferByteBuf(content, bc)
            } finally {
                content.release()
            }

            if (message.isEndStream) {
                break
            }
        }
    } catch (closed: ClosedReceiveChannelException) {
    } catch (t: Throwable) {
        bc.close(t)
    } finally {
        bc.flushAndClose()
    }
}
