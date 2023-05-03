/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.quic.bytes.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

public interface QUICStream {
    public val streamId: Long

    public val output: Output

    public val input: Input

    public val isUnidirectional: Boolean

    public val isBidirectional: Boolean

    public val isClientInitiated: Boolean

    public val isServerInitiated: Boolean

    public fun reset(errorCode: Long)

    public fun abortReading()
}

internal class QUICStreamImpl(
    override val streamId: Long,
    override val output: QUICOutputStream,
    override val input: QUICInputStream,
) : QUICStream {
    override val isUnidirectional: Boolean = (streamId and PARITY_BIT) == PARITY_BIT

    override val isBidirectional: Boolean = !isUnidirectional

    override val isServerInitiated: Boolean = (streamId and ROLE_BIT) == ROLE_BIT

    override val isClientInitiated: Boolean = !isServerInitiated

    fun appendDataToInput(data: ByteReadPacket) {
        input.appendData(data)
    }

    override fun reset(errorCode: Long) {
        TODO("Not yet implemented")
    }

    override fun abortReading() {
        TODO("Not yet implemented")
    }

    companion object {
        private const val ROLE_BIT = 0x1L
        private const val PARITY_BIT = 0x2L
    }
}

internal class QUICOutputStream(
    private val send: (data: ByteArray, offset: Long, fin: Boolean) -> Unit,
) : Output() {
    private var lastOffset: Long = -1
    private var lastChunk: ByteArray = EMPTY_BYTE_ARRAY

    override fun flush(source: Memory, offset: Int, length: Int) {
        if (lastOffset != -1L) {
            send(lastChunk, lastOffset, false)
        } else {
            lastOffset = 0
        }

        lastOffset += lastChunk.size
        lastChunk = ByteArray(length)

        source.copyTo(lastChunk, offset, length)
    }

    override fun closeDestination() {
        if (lastOffset == -1L) {
            lastOffset = 0
        }

        send(lastChunk, lastOffset, true)
    }
}

internal class QUICInputStream : Input() {
    private val buffer: BytePacketBuilder = BytePacketBuilder()
    private var bufferOffset = 0L

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        return buffer.preview {
            it.discard(bufferOffset)
            it.readAvailable(destination, offset, length).also {  count ->
                bufferOffset += count
            }
        }
    }

    fun appendData(data: ByteReadPacket) {
        data.copyTo(buffer)
    }

    override fun closeSource() {
    }
}
