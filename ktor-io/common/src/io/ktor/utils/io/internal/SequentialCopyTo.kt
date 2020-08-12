package io.ktor.utils.io.internal

import io.ktor.utils.io.ByteChannelSequentialBase
import io.ktor.utils.io.close
import io.ktor.utils.io.core.internal.ChunkBuffer

internal suspend fun ByteChannelSequentialBase.joinToImpl(dst: ByteChannelSequentialBase, closeOnEnd: Boolean) {
    copyToSequentialImpl(dst, Long.MAX_VALUE)
    if (closeOnEnd) dst.close()
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of copied bytes
 */
internal suspend fun ByteChannelSequentialBase.copyToSequentialImpl(dst: ByteChannelSequentialBase, limit: Long): Long {
    require(this !== dst)

    var remainingLimit = limit

    while (remainingLimit > 0) {
        if (!awaitInternalAtLeast1()) {
            break
        }
        val transferred = transferTo(dst, remainingLimit)

        val copied = if (transferred == 0L) {
            val tail = copyToTail(dst, remainingLimit)
            if (tail == 0L) {
                break
            }

            tail
        } else {
            if (dst.availableForWrite == 0) {
                dst.awaitAtLeastNBytesAvailableForWrite(1)
            }

            transferred
        }

        remainingLimit -= copied
    }

    return limit - remainingLimit
}

private suspend fun ByteChannelSequentialBase.copyToTail(dst: ByteChannelSequentialBase, limit: Long): Long {
    val lastPiece = ChunkBuffer.Pool.borrow()
    try {
        lastPiece.resetForWrite(limit.coerceAtMost(lastPiece.capacity.toLong()).toInt())
        val rc = readAvailable(lastPiece)
        if (rc == -1) {
            lastPiece.release(ChunkBuffer.Pool)
            return 0
        }

        dst.writeFully(lastPiece)
        return rc.toLong()
    } finally {
        lastPiece.release(ChunkBuffer.Pool)
    }
}
