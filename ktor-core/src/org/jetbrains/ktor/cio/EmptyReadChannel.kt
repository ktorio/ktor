package org.jetbrains.ktor.cio

import java.nio.*

object EmptyReadChannel : ReadChannel {
    override suspend fun read(dst: ByteBuffer) = -1
    override fun close() {}
}