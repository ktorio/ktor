/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.netty.http.*
import io.ktor.utils.io.*
import io.netty.buffer.*
import io.netty.handler.codec.http3.*
import kotlinx.coroutines.channels.*

internal suspend fun ReceiveChannel<Http3DataFrame>.http3frameLoop(bc: ByteWriteChannel) {
    try {
        while (true) {
            val message = receive()
            val content = message.content() ?: Unpooled.EMPTY_BUFFER

            transferByteBuf(content, bc)
            content.release()
        }
    } catch (closed: ClosedReceiveChannelException) {
    } catch (t: Throwable) {
        bc.close(t)
    } finally {
        bc.flushAndClose()
    }
}
