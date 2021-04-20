package io.ktor.utils.io.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

internal class ReadSessionImpl(private val channel: ByteBufferChannel) : SuspendableReadSession {
    private var lastAvailable = 0
    private var lastView: ChunkBuffer = ChunkBuffer.Empty

    public fun completed() {
        completed(ChunkBuffer.Empty)
    }

    private fun completed(newView: ChunkBuffer) {
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

    override fun request(atLeast: Int): ChunkBuffer? = channel.request(0, atLeast)?.let {
        ChunkBuffer(it).also {
            it.resetForRead()
            completed(it)
        }
    }

    override suspend fun await(atLeast: Int): Boolean {
        completed()
        return channel.awaitAtLeast(atLeast)
    }
}
