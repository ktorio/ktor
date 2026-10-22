/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.collections.*
import kotlinx.coroutines.sync.*

/**
 * Opens client sockets while limiting how many are open at once.
 *
 * [connectionsLimit] caps the total number of open sockets and [addressConnectionsLimit] the number of sockets
 * open to the same address. A socket returned from [connect] holds one permit of each limit until [release] is
 * called with its address.
 *
 * [openSocket] opens a socket on [selector]. The default connects a TCP socket; the parameter exists only so
 * tests can intercept a socket right after it has connected.
 */
internal class ConnectionFactory(
    private val selector: SelectorManager,
    connectionsLimit: Int,
    private val addressConnectionsLimit: Int,
    private val openSocket: suspend (
        selector: SelectorManager,
        address: SocketAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit
    ) -> Socket = { selectorManager, address, configuration ->
        aSocket(selectorManager).tcp().connect(address, configuration)
    },
) {
    private val limit = Semaphore(connectionsLimit)
    private val addressLimit = ConcurrentMap<SocketAddress, Semaphore>()

    suspend fun connect(
        address: SocketAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket {
        limit.acquire()
        return try {
            val addressSemaphore = addressLimit.computeIfAbsent(address) { Semaphore(addressConnectionsLimit) }
            addressSemaphore.acquire()

            try {
                openSocket(selector, address, configuration)
            } catch (cause: Throwable) {
                // a failure or cancellation
                addressSemaphore.release()
                throw cause
            }
        } catch (cause: Throwable) {
            limit.release()
            throw cause
        }
    }

    fun release(address: SocketAddress) {
        addressLimit[address]?.release()
        limit.release()
    }
}
