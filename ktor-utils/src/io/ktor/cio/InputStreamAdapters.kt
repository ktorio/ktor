package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.ByteBuffer


private class ByteReadChannelInputStream(
        private val channel: ByteReadChannel,
        private val parent: Job
) : InputStream() {
    override fun read(): Int = runBlocking(Unconfined + parent) {
        try {
            return@runBlocking channel.readByte().toInt() and 0xff
        } catch (cause: NoSuchElementException) {
            return@runBlocking -1
        }
    }

    override fun read(array: ByteArray, offset: Int, length: Int): Int = runBlocking(Unconfined + parent) {
        return@runBlocking channel.readAvailable(array, offset, length)
    }

    override fun close() {
        channel.cancel()
        parent.cancel()
    }
}

fun ByteReadChannel.toInputStream(parent: Job = Job()): InputStream =
        ByteReadChannelInputStream(this, parent)

fun InputStream.toByteReadChannel(
        pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool,
        parent: Job = Job()
): ByteReadChannel = writer(Unconfined, parent = parent, autoFlush = true) {
    val buffer = pool.borrow()
    try {
        while (true) {
            buffer.clear()
            val readCount = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (readCount < 0) break
            if (readCount == 0) continue

            buffer.position(buffer.position() + readCount)
            buffer.flip()
            channel.writeFully(buffer)
        }
    } catch (cause: Throwable) {
        channel.close(cause)
    } finally {
        pool.recycle(buffer)
        close()
    }
}.channel
