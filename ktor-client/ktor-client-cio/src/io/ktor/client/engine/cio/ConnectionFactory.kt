package io.ktor.client.engine.cio

import io.ktor.client.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.tls.*
import java.net.*
import javax.net.ssl.*

internal class ConnectionFactory(maxConnectionsCount: Int) {
    private val semaphore = Semaphore(maxConnectionsCount)

    suspend fun connect(address: InetSocketAddress): Socket {
        semaphore.enter()
        return aSocket().tcpNoDelay().tcp().connect(address)
    }

    fun release() {
        semaphore.leave()
    }
}
