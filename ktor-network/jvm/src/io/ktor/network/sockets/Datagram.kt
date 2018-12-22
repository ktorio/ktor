package io.ktor.network.sockets

import io.ktor.util.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*
import java.net.*

internal const val MAX_DATAGRAM_SIZE = 65535

/**
 * UDP datagram with [packet] content targeted to [address]
 * @property packet content
 * @property address to send to
 */
class Datagram(val packet: ByteReadPacket, val address: SocketAddress) {
    init {
        require(packet.remaining <= MAX_DATAGRAM_SIZE) { "Datagram size limit exceeded: ${packet.remaining} of possible $MAX_DATAGRAM_SIZE" }
    }
}

/**
 * A channel for sending datagrams
 */
@KtorExperimentalAPI
interface DatagramWriteChannel {
    /**
     * Datagram outgoing channel
     */
    val outgoing: SendChannel<Datagram>

    /**
     * Send datagram
     */
    suspend fun send(datagram: Datagram) {
        outgoing.send(datagram)
    }
}

/**
 * A channel for receiving datagrams
 */
@KtorExperimentalAPI
interface DatagramReadChannel {
    /**
     * Incoming datagrams channel
     */
    val incoming: ReceiveChannel<Datagram>

    /**
     * Receive a datagram
     */
    suspend fun receive(): Datagram = incoming.receive()
}

/**
 * A channel for sending and receiving datagrams
 */
interface DatagramReadWriteChannel : DatagramReadChannel, DatagramWriteChannel

/**
 * Represents a bound datagram socket
 */
interface BoundDatagramSocket : ASocket, ABoundSocket, AReadable, DatagramReadWriteChannel

/**
 * Represents a connected datagram socket.
 */
interface ConnectedDatagramSocket : ASocket, ABoundSocket, AConnectedSocket, ReadWriteSocket, DatagramReadWriteChannel
