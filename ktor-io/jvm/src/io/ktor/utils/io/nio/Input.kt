package io.ktor.utils.io.nio

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import java.nio.channels.*

private class ChannelAsInput(
    private val channel: ReadableByteChannel,
    pool: ObjectPool<ChunkBuffer>
) : Input(pool = pool) {
    init {
        require(channel !is SelectableChannel || !channel.isBlocking) { "Non-blocking channels are not supported" }
    }

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        return channel.read(destination.buffer.sliceSafe(offset, length)).coerceAtLeast(0)
    }

    override fun closeSource() {
        channel.close()
    }
}

public fun ReadableByteChannel.asInput(
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): Input = ChannelAsInput(this, pool)
