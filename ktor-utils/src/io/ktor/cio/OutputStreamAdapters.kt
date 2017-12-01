package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.charset.*


private class OutputStreamFromByteWriteChannel(private val channel: ByteWriteChannel) : OutputStream() {
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

fun ByteWriteChannel.toOutputStream(): OutputStream = OutputStreamFromByteWriteChannel(this)

suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) =
        writeFully(string.toByteArray(charset))

fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter =
        toOutputStream().bufferedWriter(charset)

fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer =
        toOutputStream().writer(charset)

