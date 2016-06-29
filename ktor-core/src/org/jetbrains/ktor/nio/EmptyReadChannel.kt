package org.jetbrains.ktor.nio

import java.nio.*

object EmptyReadChannel : ReadChannel {
    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        handler.successEnd()
    }

    override fun close() {
    }
}