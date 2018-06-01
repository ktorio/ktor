package io.ktor.network.sockets

import kotlinx.coroutines.experimental.channels.*
import kotlinx.io.core.*
import java.net.*

internal const val MAX_DATAGRAM_SIZE = 65535

class Datagram(val packet: ByteReadPacket, val address: SocketAddress) {
    init {
        require(packet.remaining <= MAX_DATAGRAM_SIZE) { "Datagram size limit exceeded: ${packet.remaining} of possible $MAX_DATAGRAM_SIZE" }
    }
}

interface DatagramWriteChannel {
    val outgoing: SendChannel<Datagram>

    suspend fun send(datagram: Datagram) {
        outgoing.send(datagram)
    }
}

interface DatagramReadChannel {
    val incoming: ReceiveChannel<Datagram>

    suspend fun receive(): Datagram = incoming.receive()
}

interface DatagramReadWriteChannel : DatagramReadChannel, DatagramWriteChannel

interface BoundDatagramSocket : ASocket, ABoundSocket, AReadable, DatagramReadWriteChannel

interface ConnectedDatagramSocket : ASocket, ABoundSocket, AConnectedSocket, ReadWriteSocket, DatagramReadWriteChannel
