package io.ktor.utils.io.nio

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.sliceSafe
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import java.nio.channels.*

private class ChannelAsOutput(
    pool: ObjectPool<ChunkBuffer>,
    val channel: WritableByteChannel
) : Output(pool) {
    override fun flush(source: Memory, offset: Int, length: Int) {
        val slice = source.buffer.sliceSafe(offset, length)
        while (slice.hasRemaining()) {
            channel.write(slice)
        }
    }

    override fun closeDestination() {
        channel.close()
    }
}

public fun WritableByteChannel.asOutput(
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): Output = ChannelAsOutput(pool, this)
