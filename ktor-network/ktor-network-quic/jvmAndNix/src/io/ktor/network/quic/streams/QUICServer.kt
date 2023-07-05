/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.streams

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.sockets.*
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.network.sockets.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class QUICServer(datagramSocket: BoundDatagramSocket, options: SocketOptions.QUICSocketOptions) :
    QUICSocketBase(datagramSocket),
    BoundQUICSocket,
    AReadable by datagramSocket {

    override val logger: Logger = logger()

    private val tlsServerComponentFactory: TLSServerComponentFactory

    init {
        val certificatePath = options.certificatePath
            ?: error("Expected 'certificatePath' in QUIC Socket options for server")
        val privateKeyPath = options.privateKeyPath
            ?: error("Expected 'privateKeyPath' in QUIC Socket options for server")

        tlsServerComponentFactory = tlsServerComponentFactory(certificatePath, privateKeyPath)
    }

    private val _incoming = Channel<QUICStream>(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<QUICStream> = _incoming

    override suspend fun createConnection(
        address: SocketAddress,
        peerSourceConnectionID: ConnectionID,
        originalDestinationConnectionID: ConnectionID,
    ): QuicConnection {
        val sourceConnectionID = ConnectionID.new()

        return coroutineScope {
            QuicConnection(
                isServer = true,
                initialLocalConnectionID = sourceConnectionID,
                originalDestinationConnectionID = originalDestinationConnectionID,
                initialPeerConnectionID = peerSourceConnectionID,
                connectionIDLength = sourceConnectionID.size,
                tlsComponentProvider = { tlsServerComponentFactory.createTLSServerComponent(it) },
                outgoingDatagramChannel = datagramSocket.outgoing,
                streamChannel = _incoming,
                initialSocketAddress = address,
            ).apply {
                tlsComponent.acceptOriginalDcid(originalDestinationConnectionID)
            }
        }
    }
}
