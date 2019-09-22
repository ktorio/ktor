@file:Suppress("DEPRECATION")

package io.ktor.utils.io.internal

import io.ktor.utils.io.ByteBufferChannel
import io.ktor.utils.io.SuspendableReadSession
import io.ktor.utils.io.core.IoBuffer

internal class ReadSessionImpl(private val channel: ByteBufferChannel) : SuspendableReadSession {
    private var lastAvailable = 0
    private var lastView: IoBuffer = IoBuffer.Empty

    fun completed() {
        completed(IoBuffer.Empty)
    }

    private fun completed(newView: IoBuffer) {
        val delta = lastAvailable - lastView.readRemaining
        if (delta > 0) {
            channel.consumed(delta)
        }
        lastView = newView
        lastAvailable = newView.readRemaining
    }

    override val availableForRead: Int
        get() = channel.availableForRead

    override fun discard(n: Int): Int {
        completed()
        val quantity = minOf(availableForRead, n)
        channel.consumed(quantity)
        return quantity
    }

    override fun request(atLeast: Int): IoBuffer? {
        return channel.request(0, atLeast)?.let { IoBuffer(it).also { it.resetForRead(); completed(it) } }
    }

    override suspend fun await(atLeast: Int): Boolean {
        completed()
        return channel.awaitAtLeast(atLeast)
    }
}
