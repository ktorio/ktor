package org.jetbrains.ktor.nio

import java.nio.*

object AsyncEmptyChannel : AsyncReadChannel {
    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        handler.successEnd()
    }

    override fun close() {
    }
}