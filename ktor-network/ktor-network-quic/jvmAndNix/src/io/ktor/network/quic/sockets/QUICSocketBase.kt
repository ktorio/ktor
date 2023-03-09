/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNUSED_PARAMETER")

package io.ktor.network.quic.sockets

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.streams.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

internal abstract class QUICSocketBase(
    private val datagramSocket: BoundDatagramSocket,
) : QUICStreamReadWriteChannel, ASocket by datagramSocket, ABoundSocket by datagramSocket {

    override fun dispose() {
        datagramSocket.dispose()
    }

    protected suspend fun sendDatagram(packet: ByteReadPacket, address: SocketAddress) {
        datagramSocket.send(Datagram(packet, address))
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    receiveAndProcessDatagram()
                } catch (e : Exception) {
                    println(e)
//                    e.printStackTrace()
                }
                println("-".repeat(70))
            }
        }
    }

    private suspend fun receiveAndProcessDatagram() {
        val datagram = datagramSocket.receive()
        println("Accepted datagram from ${datagram.address}")
        println("packet size: ${datagram.packet.remaining}")

        while (datagram.packet.isNotEmpty) {
            val packet = PacketReader.readSinglePacket(
                bytes = datagram.packet,
                negotiatedVersion = 0u, // todo
                dcidLength = 0u, // todo
                matchConnection = { dcid, type -> },
                raiseError = {
                    handleTransportError(it)
                    error(it.toString())
                }
            )

            processIncomingPacket(datagram.address, packet)
        }
    }

    abstract suspend fun processIncomingPacket(address: SocketAddress, packet: QUICPacket)

    private suspend fun handleTransportError(error: QUICTransportError) {}
}
