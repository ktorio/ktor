@file:Suppress("DEPRECATION")

package io.ktor.utils.io.internal

import io.ktor.utils.io.ByteBufferChannel
import io.ktor.utils.io.WriterSuspendSession
import io.ktor.utils.io.core.ByteOrder
import io.ktor.utils.io.core.IoBuffer
import java.nio.ByteBuffer

internal class WriteSessionImpl(channel: ByteBufferChannel) : WriterSuspendSession {
    private var locked = 0

    private var current = channel.resolveChannelInstance()
    private var byteBuffer = IoBuffer.Empty.memory.buffer
    private var view = IoBuffer.Empty
    private var ringBufferCapacity = current.currentState().capacity

    fun begin() {
        current = current.resolveChannelInstance()
        byteBuffer = current.setupStateForWrite() ?: return
        view = IoBuffer(current.currentState().backingBuffer)
        view.resetFromContentToWrite(byteBuffer)
        ringBufferCapacity = current.currentState().capacity
    }

    fun complete() {
        if (locked > 0) {
            ringBufferCapacity.completeRead(locked)
            locked = 0
        }

        current.restoreStateAfterWrite()
        current.tryTerminate()
    }

    override fun request(min: Int): IoBuffer? {
        locked += ringBufferCapacity.tryWriteAtLeast(0)
        if (locked < min) return null
        current.prepareWriteBuffer(byteBuffer, locked)
        if (byteBuffer.remaining() < min) return null
        //if (current.joining != null) return null
        @Suppress("DEPRECATION")
        view.resetFromContentToWrite(byteBuffer)

        return view
    }

    override fun written(n: Int) {
        if (n < 0 || n > locked) {
            writtenFailed(n)
        }
        locked -= n
        current.bytesWrittenFromSesion(byteBuffer, ringBufferCapacity, n)
    }

    private fun writtenFailed(n: Int): Nothing {
        if (n < 0) {
            throw IllegalArgumentException("Written bytes count shouldn't be negative: $n")
        }

        throw IllegalStateException("Unable to mark $n bytes as written: only $locked were pre-locked.")
    }

    override suspend fun tryAwait(n: Int) {
        val joining = current.getJoining()
        if (joining != null) {
            return tryAwaitJoinSwitch(n, joining)
        }

        if (locked >= n) return
        if (locked > 0) {
            ringBufferCapacity.completeRead(locked)
            locked = 0
        }

        return current.tryWriteSuspend(n)
    }

    private suspend fun tryAwaitJoinSwitch(n: Int, joining: ByteBufferChannel.JoiningState) {
        if (locked > 0) {
            ringBufferCapacity.completeRead(locked)
            locked = 0
        }
        flush()
        current.restoreStateAfterWrite()
        current.tryTerminate()

        do {
            current.tryWriteSuspend(n)
            current = current.resolveChannelInstance()
            byteBuffer = current.setupStateForWrite() ?: continue
            view = IoBuffer(current.currentState().backingBuffer)
            @Suppress("DEPRECATION")
            view.resetFromContentToWrite(byteBuffer)
            ringBufferCapacity = current.currentState().capacity
        } while (false)
    }

    override fun flush() {
        current.flush()
    }


    private fun ByteBuffer.prepareBuffer(order: ByteOrder, position: Int, available: Int) {
        require(position >= 0)
        require(available >= 0)

        val bufferLimit = capacity() - current.reservedSize
        val virtualLimit = position + available

        order(order.nioOrder)
        limit(virtualLimit.coerceAtMost(bufferLimit))
        position(position)
    }

}
