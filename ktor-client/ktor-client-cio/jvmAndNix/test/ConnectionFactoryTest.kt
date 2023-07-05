/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.engine.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class ConnectionFactoryTest {

    private val selectorManager = SelectorManager()

    @Test
    fun testLimitSemaphore() = runBlocking {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        withServerSocket(0) { socket0 ->
            withServerSocket(1) { socket1 ->
                withServerSocket(2) { socket2 ->
                    connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                    connectionFactory.connect(socket1.localAddress as InetSocketAddress)

                    assertTimeout {
                        connectionFactory.connect(socket2.localAddress as InetSocketAddress)
                    }
                }
            }
        }
    }

    @Test
    fun testAddressSemaphore() = runBlocking {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        withServerSocket(0) { socket0 ->

            withServerSocket(1) { socket1 ->
                connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                assertTimeout {
                    connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                }

                connectionFactory.connect(socket1.localAddress as InetSocketAddress)
                assertTimeout {
                    connectionFactory.connect(socket1.localAddress as InetSocketAddress)
                }
            }
        }
    }

    @Test
    fun testReleaseLimitSemaphoreWhenFailed() = runBlocking {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        withServerSocket(0) { socket0 ->
            withServerSocket(1) { socket1 ->
                connectionFactory.connect(socket0.localAddress as InetSocketAddress)

                // Release the `limit` semaphore when it fails to acquire the address semaphore.
                assertTimeout {
                    connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                }

                connectionFactory.connect(socket1.localAddress as InetSocketAddress)
            }
        }
    }

    private suspend fun assertTimeout(timeoutMillis: Long = 500, block: suspend () -> Unit) {
        assertFailsWith(TimeoutCancellationException::class) {
            withTimeout(timeoutMillis) {
                block()
            }
        }
    }

    private suspend fun withServerSocket(port: Int, block: suspend (ServerSocket) -> Unit) {
        selectorManager.use {
            aSocket(it).tcp().bind(TEST_SERVER_SOCKET_HOST, port).use { socket ->
                block(socket)
            }
        }
    }

    companion object {
        private const val TEST_SERVER_SOCKET_HOST = "0.0.0.0"
    }
}
