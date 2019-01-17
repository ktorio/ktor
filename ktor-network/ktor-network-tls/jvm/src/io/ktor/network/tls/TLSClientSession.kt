package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*
import kotlin.coroutines.*

internal suspend fun openTLSSession(
    socket: Socket,
    input: ByteReadChannel, output: ByteWriteChannel,
    config: TLSConfig,
    context: CoroutineContext
): Socket {
    val handshake = TLSClientHandshake(input, output, config, context)
    handshake.negotiate()
    return TLSSocket(handshake.input, handshake.output, socket, context)
}

private class TLSSocket(
    private val input: ReceiveChannel<TLSRecord>,
    private val output: SendChannel<TLSRecord>,
    socket: Socket,
    override val coroutineContext: CoroutineContext
) : CoroutineScope, Socket by socket {

    override fun attachForReading(channel: ByteChannel): WriterJob = writer(coroutineContext, channel) {
        appDataInputLoop(this.channel)
    }

    override fun attachForWriting(channel: ByteChannel): ReaderJob = reader(coroutineContext, channel) {
        appDataOutputLoop(this.channel)
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    private suspend fun appDataInputLoop(pipe: ByteWriteChannel) {
        try {
            input.consumeEach { record ->
                val packet = record.packet
                val length = packet.remaining
                when (record.type) {
                    TLSRecordType.ApplicationData -> {
                        pipe.writePacket(record.packet)
                        pipe.flush()
                    }
                    else -> throw TLSException("Unexpected record ${record.type} ($length bytes)")
                }
            }
        } catch (cause: Throwable) {
        } finally {
            pipe.close()
        }
    }

    private suspend fun appDataOutputLoop(
        pipe: ByteReadChannel
    ): Unit = DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
        while (true) {
            buffer.clear()
            val rc = pipe.readAvailable(buffer)
            if (rc == -1) break

            buffer.flip()
            output.send(TLSRecord(TLSRecordType.ApplicationData, packet = buildPacket { writeFully(buffer) }))
        }
    }
}
