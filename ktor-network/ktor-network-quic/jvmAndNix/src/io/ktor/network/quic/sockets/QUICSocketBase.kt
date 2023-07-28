/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNUSED_PARAMETER")

package io.ktor.network.quic.sockets

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.streams.*
import io.ktor.network.sockets.*
import io.ktor.util.logging.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

internal abstract class QUICSocketBase(
    protected val datagramSocket: BoundDatagramSocket,
) : QUICStreamReadChannel, ASocket by datagramSocket, ABoundSocket by datagramSocket {
    protected abstract val logger: Logger
    protected abstract val role: ConnectionRole
    protected val connections = mutableListOf<QUICConnection>()

    override fun dispose() {
        datagramSocket.dispose()
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    receiveAndProcessDatagram()
                } catch (e: CancellationException) {
                    // ignore
                } catch (cause: Exception) {
                    logger.error(cause)
                }
            }
        }
    }

    private suspend fun receiveAndProcessDatagram() {
        val datagram = datagramSocket.receive()
        logger.info("Accepted datagram from ${datagram.address}")
        logger.info("Datagram size: ${datagram.packet.remaining}")

        var firstDcidInDatagram: QUICConnectionID? = null

        while (datagram.packet.isNotEmpty) {
            val packet = PacketReader.readSinglePacket(
                bytes = datagram.packet,
                firstDcidInDatagram = firstDcidInDatagram,
                matchConnection = { dcid, scid, _ ->
                    dcid.connection ?: createConnection(datagram.address, scid!!, dcid).also { connections.add(it) }
                },
                raiseError = {
                    handleTransportError(it)
                    error(it.toString())
                }
            ) ?: break

            firstDcidInDatagram = packet.destinationConnectionID

            // todo can here be null connection?
            packet.destinationConnectionID.connection!!.processPacket(packet)
        }
    }

    abstract suspend fun createConnection(
        address: SocketAddress,
        peerSourceConnectionID: QUICConnectionID,
        originalDestinationConnectionID: QUICConnectionID,
    ): QUICConnection

    private fun handleTransportError(error: QUICTransportError) {}

    private val QUICConnectionID.connection: QUICConnection? get() = connections.find { it.match(this) }
}
