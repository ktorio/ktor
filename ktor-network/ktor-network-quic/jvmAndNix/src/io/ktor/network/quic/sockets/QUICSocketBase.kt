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
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

internal abstract class QUICSocketBase(
    protected val datagramSocket: BoundDatagramSocket,
) : QUICStreamReadWriteChannel, ASocket by datagramSocket, ABoundSocket by datagramSocket {
    protected val connections = mutableListOf<QUICConnection_v1>()

    override fun dispose() {
        datagramSocket.dispose()
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    receiveAndProcessDatagram()
                } catch (e: Exception) {
                    println(e)
                    e.printStackTrace()
                }
                println("-".repeat(70))
            }
        }
    }

    private suspend fun receiveAndProcessDatagram() {
        val datagram = datagramSocket.receive()
        println("Accepted datagram from ${datagram.address}")
        println("Datagram size: ${datagram.packet.remaining}")

        while (datagram.packet.isNotEmpty) {
            val packet = PacketReader.readSinglePacket(
                bytes = datagram.packet,
                matchConnection = { dcid, scid, _ ->
                    dcid.connection ?: createConnection(datagram.address, scid!!, dcid).also { connections.add(it) }
                },
                raiseError = {
                    handleTransportError(it)
                    error(it.toString())
                }
            )

            println("Decrypted packet overview:")
            println(packet.toDebugString())

            // todo can here be null connection?
            packet.destinationConnectionID.connection!!.processPacket(packet)
        }
    }

    abstract suspend fun createConnection(
        address: SocketAddress,
        peerSourceConnectionID: ConnectionID,
        originalDestinationConnectionID: ConnectionID,
    ): QUICConnection_v1

    private fun handleTransportError(error: QUICTransportError) {}

    private val ConnectionID.connection: QUICConnection_v1? get() = connections.find { it.match(this) }
}
