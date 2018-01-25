package io.ktor.client.engine.cio

import io.ktor.client.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import java.net.*

internal class ConnectionFactory(maxConnectionsCount: Int) {
    private val semaphore = Semaphore(maxConnectionsCount)

    suspend fun connect(address: SocketAddress): Socket {
        semaphore.enter()
        return aSocket().tcpNoDelay().tcp().connect(address)

    }

    fun release() {
        semaphore.leave()
    }
}
