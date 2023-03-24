/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.sockets.*
import io.ktor.network.quic.tls.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*

internal class QUICServer(datagramSocket: BoundDatagramSocket, options: SocketOptions.QUICSocketOptions) :
    QUICSocketBase(datagramSocket),
    BoundQUICSocket,
    AReadable by datagramSocket {
    private val tlsServerComponentFactory: TLSServerComponentFactory

    init {
        val certificatePath = options.certificatePath
            ?: error("Expected 'certificatePath' in QUIC Socket options for server")
        val privateKeyPath = options.privateKeyPath
            ?: error("Expected 'privateKeyPath' in QUIC Socket options for server")

        tlsServerComponentFactory = tlsServerComponentFactory(certificatePath, privateKeyPath)
    }

    private val _incoming = Channel<QUICStream>(Channel.UNLIMITED)

    override val outgoing: SendChannel<QUICStream>
        get() = TODO("Not yet implemented")
    override val incoming: ReceiveChannel<QUICStream> = _incoming

    override suspend fun processIncomingPacket(address: SocketAddress, packet: QUICPacket) {
        println("sending packet")
        (packet as InitialPacket_v1)
        _incoming.send(
            """
            |packet overview 
            |packet number: ${packet.packetNumber} 
            |scid: ${packet.sourceConnectionID.value.joinToString(" ") { it.toString16Byte() }}
            |dcid: ${packet.destinationConnectionID.value.joinToString(" ") { it.toString16Byte() }}
            |token: ${packet.token.joinToString("")}
            |version: ${packet.version}
        """.trimMargin().toByteArray()
        )
        // |payload: ${packet.payload.readBytes().joinToString(" ") { "%02x".format(it) }}
        packet.payload.readBytes()
    }

    override fun createConnection(peerSourceConnectionID: ConnectionID): QUICConnection_v1 {
        val communicationProvider = ProtocolCommunicationProvider(
            sendCryptoFrame = {
            },
            raiseError = {
                handleTransportError(it)
                error("")
            },
            getTransportParameters = {
                transportParameters()
            }
        )

        val sourceConnectionID = ConnectionID.new(peerSourceConnectionID.size)
        val tls = tlsServerComponentFactory.createTLSServerComponent(communicationProvider)

        return QUICConnection_v1(
            tlsComponent = tls,
            isServer = true,
            initialLocalConnectionID = sourceConnectionID,
            initialPeerConnectionID = peerSourceConnectionID,
            connectionIDLength = peerSourceConnectionID.size
        )
    }

    private fun Byte.toString16Byte(): String {
        return toUByte().toString(16).padStart(2, '0')
    }
}
