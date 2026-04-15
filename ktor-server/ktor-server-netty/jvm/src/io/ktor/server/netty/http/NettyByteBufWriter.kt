/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http

import io.ktor.utils.io.*
import io.netty.buffer.*

/**
 * Transfers all readable bytes from a [ByteBuf] into a [ByteWriteChannel],
 * then flushes the channel. This is shared logic used by both HTTP/2 and HTTP/3
 * frame adapters when converting protocol-specific data frames into a byte channel.
 */
internal suspend fun transferByteBuf(content: ByteBuf, bc: ByteWriteChannel) {
    while (content.readableBytes() > 0) {
        bc.write { bb ->
            val size = content.readableBytes()
            if (bb.remaining() > size) {
                val l = bb.limit()
                bb.limit(bb.position() + size)
                content.readBytes(bb)
                bb.limit(l)
            } else {
                content.readBytes(bb)
            }
        }
    }
    bc.flush()
}
