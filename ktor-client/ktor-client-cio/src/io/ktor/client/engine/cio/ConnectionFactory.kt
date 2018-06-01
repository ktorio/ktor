package io.ktor.client.engine.cio

import io.ktor.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import java.net.*

internal class ConnectionFactory(private val selector: SelectorManager, maxConnectionsCount: Int) {
    private val semaphore = Semaphore(maxConnectionsCount)

    suspend fun connect(address: InetSocketAddress): Socket {
        semaphore.enter()
        return aSocket(selector).tcpNoDelay().tcp().connect(address)
    }

    fun release() {
        semaphore.leave()
    }
}
