/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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

@Suppress("DEPRECATION")
internal class TLSSocket(
    private val version: TLSVersion,
    private val input: ReceiveChannel<TLSRecord>,
    private val output: SendChannel<TLSRecord>,
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
            input.consumeEach { record ->
                val packet = record.packet
                val length = packet.remaining
                when (record.type) {
                    TLSRecordType.ApplicationData -> {
                        pipe.writePacket(record.packet)
                        pipe.flush()
                    }
                    else -> throw TLSValidationException("Unexpected record ${record.type} ($length bytes)")
                }
            }
        } catch (_: Throwable) {
        } finally {
            pipe.close()
        }
    }

    private suspend fun appDataOutputLoop(pipe: ByteReadChannel): Unit =
        DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
            try {
                while (true) {
                    buffer.clear()
                    val rc = pipe.readAvailable(buffer)
                    if (rc == -1) break

                    buffer.flip()
                    output.send(TLSRecord(TLSRecordType.ApplicationData, version, buildPacket { writeFully(buffer) }))
                }
            } catch (_: ClosedSendChannelException) {
                // The socket was already closed, we should ignore that error.
            } finally {
                output.close()
            }
        }

    override fun dispose() {
        socket.dispose()
    }
}
