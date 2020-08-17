/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.sync.*

internal class ConnectionFactory(
    private val selector: SelectorManager,
    maxConnectionsCount: Int
) {
    private val semaphore = Semaphore(maxConnectionsCount)

    suspend fun connect(
        address: NetworkAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket {
        semaphore.acquire()
        return try {
            aSocket(selector).tcpNoDelay().tcp().connect(address, configuration)
        } catch (cause: Throwable) {
            // a failure or cancellation
            semaphore.release()
            throw cause
        }
    }

    fun release() {
        semaphore.release()
    }
}
