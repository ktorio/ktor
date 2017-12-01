package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.ByteBuffer


private class ByteReadChannelInputStream(private val channel: ByteReadChannel) : InputStream() {
    override fun read(): Int = runBlocking(Unconfined) {
        try {
            return@runBlocking channel.readByte().toInt() and 0xff
        } catch (cause: NoSuchElementException) {
            return@runBlocking -1
        }
    }

    override fun read(array: ByteArray, offset: Int, length: Int): Int = runBlocking(Unconfined) {
        return@runBlocking channel.readAvailable(array, offset, length)
    }
}

fun ByteReadChannel.toInputStream(): InputStream = ByteReadChannelInputStream(this)

fun InputStream.toByteReadChannel(pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool): ByteReadChannel = writer(Unconfined, autoFlush = true) {
    val buffer = pool.borrow()
    while (true) {
        buffer.clear()
        val readCount = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
        if (readCount < 0) break
        if (readCount == 0) continue

        buffer.position(buffer.position() + readCount)
        buffer.flip()
        channel.writeFully(buffer)
    }

    pool.recycle(buffer)
    close()
}.channel
