package org.jetbrains.ktor.cio

import kotlinx.coroutines.experimental.*
import java.io.*
import java.nio.*

class InputStreamFromReadChannel(val channel: ReadChannel, val bufferPool: ByteBufferPool = NoPool) : InputStream() {
    private val singleByte = bufferPool.allocate(1)
    override fun read(): Int = runBlocking(Unconfined) {
        singleByte.buffer.clear()

        while (true) {
            val count = channel.read(singleByte.buffer)
            if (count == -1)
                return@runBlocking -1
            else if (count == 1)
                break
        }

        singleByte.buffer.flip()
        singleByte.buffer.get().toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = runBlocking(Unconfined) {
        val bb = ByteBuffer.wrap(b, off, len)
        channel.read(bb)
    }

    override fun close() {
        super.close()
        bufferPool.release(singleByte)
    }
}

private class ReadChannelFromInputStream(val input: InputStream) : ReadChannel {
    override suspend fun read(dst: ByteBuffer): Int {
        val count = input.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining())
        if (count > 0) {
            dst.position(dst.position() + count)
        }
        return count
    }

    override fun close() {
        input.close()
    }
}


fun ReadChannel.toInputStream(): InputStream = InputStreamFromReadChannel(this)
fun InputStream.toReadChannel(): ReadChannel = ReadChannelFromInputStream(this)
