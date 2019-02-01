package io.ktor.client.engine.cio

import io.ktor.util.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import java.net.*

internal class ConnectionFactory(
    private val selector: SelectorManager,
    maxConnectionsCount: Int
) {
    private val semaphore = Semaphore(maxConnectionsCount)

    suspend fun connect(address: InetSocketAddress): Socket {
        semaphore.enter()
        return try {
            aSocket(selector).tcpNoDelay().tcp().connect(address)
        } catch (cause: Throwable) {
            // a failure or cancellation
            semaphore.leave()
            throw cause
        }
    }

    fun release() {
        semaphore.leave()
    }
}
