/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    receiveAndProcessDatagram()
                }
            } catch (_: CancellationException) {
                // ignore
            }
        }
    }

    abstract suspend fun processIncomingPacket(address: SocketAddress, datagram: QUICPacket)

    override fun dispose() {
        datagramSocket.dispose()
    }

    protected suspend fun sendDatagram(packet: ByteReadPacket, address: SocketAddress) {
        datagramSocket.send(Datagram(packet, address))
    }

    private suspend fun receiveAndProcessDatagram() {
        val datagram = datagramSocket.receive()
        val packet = PacketReader.readSinglePacket(
            bytes = datagram.packet,
            negotiatedVersion = 0u, // todo
            onError = {
                handleTransportError(it)
                return
            }
        )

        processIncomingPacket(datagram.address, packet)
    }

    private suspend fun handleTransportError(error: QUICTransportError) {

    }
}