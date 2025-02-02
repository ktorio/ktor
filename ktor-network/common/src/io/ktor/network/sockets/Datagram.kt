/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*

internal const val MAX_DATAGRAM_SIZE = 65535

/**
 * UDP datagram with [packet] content targeted to [address]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Datagram)
 *
 * @property packet content
 * @property address to send to
 */
public class Datagram(
    public val packet: Source,
    public val address: SocketAddress
) {
    init {
        require(packet.remaining <= MAX_DATAGRAM_SIZE) {
            "Datagram size limit exceeded: ${packet.remaining} of possible $MAX_DATAGRAM_SIZE"
        }
    }
}

/**
 * A channel for sending datagrams
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramWriteChannel)
 */
public interface DatagramWriteChannel {
    /**
     * Datagram outgoing channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramWriteChannel.outgoing)
     */
    public val outgoing: SendChannel<Datagram>

    /**
     * Send datagram.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramWriteChannel.send)
     */
    public suspend fun send(datagram: Datagram) {
        outgoing.send(datagram)
    }
}

/**
 * A channel for receiving datagrams
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramReadChannel)
 */
public interface DatagramReadChannel {
    /**
     * Incoming datagrams channel
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramReadChannel.incoming)
     */
    public val incoming: ReceiveChannel<Datagram>

    /**
     * Receive a datagram.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramReadChannel.receive)
     */
    public suspend fun receive(): Datagram = incoming.receive()
}

/**
 * A channel for sending and receiving datagrams
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.DatagramReadWriteChannel)
 */
public interface DatagramReadWriteChannel : DatagramReadChannel, DatagramWriteChannel

/**
 * Represents a bound datagram socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.BoundDatagramSocket)
 */
public interface BoundDatagramSocket : ASocket, ABoundSocket, DatagramReadWriteChannel

/**
 * Represents a connected datagram socket.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.ConnectedDatagramSocket)
 */
public interface ConnectedDatagramSocket :
    ASocket, ABoundSocket, AConnectedSocket, DatagramReadWriteChannel
