/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.test.*

class TcpSocketTestNix {

    @Test
    fun testDescriptorClose() = testSuspend {
        val selector = SelectorManager()
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        socket.close()
        selector.close()

        selector.coroutineContext[Job]?.join()

        val isDescriptorValid = fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
        check(!isDescriptorValid) { "Descriptor was not closed" }
    }

    @Test
    fun testDescriptorCloseWithClientAndReadChannel() = testSuspend {
        val selector = SelectorManager()
        val tcp = aSocket(selector).tcp()

        val server = tcp.bind(InetSocketAddress("127.0.0.1", 0))
        val serverDescriptor = (server as TCPServerSocketNative).selectable.descriptor

        val serverConnectionPromise = async {
            server.accept()
        }

        val port = (server.localAddress as InetSocketAddress).port
        val clientConnection = tcp.connect("127.0.0.1", port)
        val clientDescriptor = (clientConnection as TCPSocketNative).selectable.descriptor

        val serverConnection = serverConnectionPromise.await()
        val serverConnectionDescriptor = (serverConnection as TCPSocketNative).selectable.descriptor

        clientConnection.openReadChannel()
        serverConnection.openReadChannel()

        serverConnection.close()
        clientConnection.close()
        server.close()
        selector.close()

        selector.coroutineContext[Job]?.join()

        val isServerDescriptorValid = fcntl(serverDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerDescriptorValid) { "Server descriptor was not closed" }

        val isServerConnectionDescriptorValid = fcntl(serverConnectionDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerConnectionDescriptorValid) { "Server connection descriptor was not closed" }

        val isClientDescriptorValid = fcntl(clientDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isClientDescriptorValid) { "Client descriptor was not closed" }
    }
}
