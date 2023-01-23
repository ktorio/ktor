/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.sockets

import io.ktor.network.quic.streams.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*

internal abstract class QUICSocketBase(
    private val datagramSocket: BoundDatagramSocket,
) : QUICStreamReadWriteChannel, ASocket by datagramSocket, ABoundSocket by datagramSocket {
    override fun dispose() {
        datagramSocket.dispose()
    }

    protected suspend fun sendDatagram(packet: ByteReadPacket, address: SocketAddress) {
        datagramSocket.send(Datagram(packet, address))
    }

    protected suspend fun receiveDatagram(): Datagram {
        return datagramSocket.receive()
    }
}
