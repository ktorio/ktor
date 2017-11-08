package io.ktor.client.utils

import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*


private val DEFAULT_RESPONSE_POOL_SIZE = 1000
internal val DEFAULT_RESPONSE_SIZE = 8192

val HTTP_CLIENT_RESPONSE_POOL = object : DefaultPool<ByteBuffer>(DEFAULT_RESPONSE_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_RESPONSE_SIZE)!!
}

fun InputStream.toByteReadChannel(): ByteReadChannel {
    return writer(ioCoroutineDispatcher) {
        val buffer = HTTP_CLIENT_RESPONSE_POOL.borrow()

        while (true) {
            buffer.clear()
            val count = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (count == 0) continue
            if (count < 0) break

            buffer.position(buffer.position() + count)
            buffer.flip()
            channel.writeFully(buffer)
        }

        close()
        HTTP_CLIENT_RESPONSE_POOL.recycle(buffer)
    }.channel
}

fun ByteArray.toByteReadChannel(): ByteReadChannel {
    return writer(ioCoroutineDispatcher) {
        channel.writeFully(this@toByteReadChannel)
    }.channel
}

fun ByteReadChannel.toInputStream(): InputStream = ByteReadChannelInputStream(this)

fun HttpMessageBody.toByteReadChannel(): ByteReadChannel {
    return when (this) {
        is EmptyBody -> EmptyByteReadChannel
        is ByteReadChannelBody -> channel
        is ByteWriteChannelBody -> {
            writer(ioCoroutineDispatcher) {
                block(channel)
            }.channel
        }
    }
}

internal class ByteReadChannelInputStream(private val channel: ByteReadChannel) : InputStream() {
    override fun read(): Int = runBlocking(Unconfined) {
        try {
            channel.readByte().toInt() and 0xff
        } finally {
            -1
        }
    }

    override fun read(array: ByteArray, offset: Int, length: Int): Int = runBlocking(Unconfined) {
        channel.readAvailable(array, offset, length)
    }
}

internal class ByteWriteChannelOutputStream(private val channel: ByteWriteChannel) : OutputStream() {
    override fun write(byte: Int) = runBlocking(Unconfined) {
        channel.writeByte(byte.toByte())
    }

    override fun write(byteArray: ByteArray, offset: Int, length: Int) = runBlocking(Unconfined) {
        channel.writeFully(byteArray, offset, length)
    }

    override fun close() {
        channel.close()
    }
}