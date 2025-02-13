/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.*
import kotlin.coroutines.*

internal actual suspend fun openTLSSession(
    socket: Socket,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    config: TLSConfig,
    context: CoroutineContext
): Socket {
    val handshake = TLSClientHandshake(input, output, config, context)
    try {
        handshake.negotiate()
    } catch (cause: Exception) {
        runCatching {
            handshake.close().await()
            socket.close()
        }
        if (cause is ClosedSendChannelException) {
            throw TlsException("Negotiation failed due to EOS", cause)
        } else {
            throw cause
        }
    }
    return TLSSocket(
        handshake,
        socket,
        context
    )
}

private class TLSSocket(
    private val base: TLSClientHandshake,
    private val socket: Socket,
    override val coroutineContext: CoroutineContext
) : CoroutineScope, Socket by socket {

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(coroutineContext + CoroutineName("cio-tls-input-loop"), channel) {
            appDataInputLoop(this.channel)
        }

    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        reader(coroutineContext + CoroutineName("cio-tls-output-loop"), channel) {
            appDataOutputLoop(this.channel)
        }

    private suspend fun appDataInputLoop(pipe: ByteWriteChannel) {
        try {
            base.input.consumeEach { record ->
                val packet = record.packet
                val length = packet.remaining
                when (record.type) {
                    TLSRecordType.ApplicationData -> {
                        pipe.writePacket(record.packet)
                        pipe.flush()
                    }
                    else -> throw TlsException("Unexpected record ${record.type} ($length bytes)")
                }
            }
        } catch (_: Throwable) {
        } finally {
            pipe.flushAndClose()
        }
    }

    private suspend fun appDataOutputLoop(
        pipe: ByteReadChannel
    ): Unit = DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
        try {
            while (true) {
                buffer.clear()
                val rc = pipe.readAvailable(buffer)
                if (rc == -1) break

                buffer.flip()
                base.output.send(TLSRecord(TLSRecordType.ApplicationData, packet = buildPacket { writeFully(buffer) }))
            }
        } catch (_: ClosedSendChannelException) {
            // The socket was already closed, we should ignore that error.
        } finally {
            base.output.close()
        }
    }

    override fun dispose() {
        close()
    }

    /**
     * The socket is closed after sending the close-notify alert.
     */
    override fun close() {
        base.close().invokeOnCompletion {
            socket.close()
        }
    }
}
