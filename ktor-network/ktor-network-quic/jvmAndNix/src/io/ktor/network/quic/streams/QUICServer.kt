/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.quic.packets.*
import io.ktor.network.quic.sockets.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*
import kotlin.text.toByteArray

internal class QUICServer(datagramSocket: BoundDatagramSocket) :
    QUICSocketBase(datagramSocket),
    BoundQUICSocket,
    AReadable by datagramSocket {

    private val _incoming = Channel<QUICStream>(Channel.UNLIMITED)

    override val outgoing: SendChannel<QUICStream>
        get() = TODO("Not yet implemented")
    override val incoming: ReceiveChannel<QUICStream> = _incoming

    override suspend fun processIncomingPacket(address: SocketAddress, packet: QUICPacket) {
        println("sending packet")
        (packet as InitialPacket_v1)
        _incoming.send("""
            |packet overview 
            |packet number: ${packet.packetNumber} 
            |scid: ${packet.sourceConnectionID.value.joinToString(" ") { "%02x".format(it) }}
            |dcid: ${packet.destinationConnectionID.value.joinToString(" ") { "%02x".format(it) }}
            |token: ${packet.token.joinToString("")}
            |version: ${packet.version}
        """.trimMargin().toByteArray())
        //|payload: ${packet.payload.readBytes().joinToString(" ") { "%02x".format(it) }}
        packet.payload.readBytes()
    }
}
