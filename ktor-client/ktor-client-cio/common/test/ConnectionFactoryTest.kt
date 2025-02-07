/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class ConnectionFactoryTest {

    private lateinit var selectorManager: SelectorManager

    @BeforeTest
    fun setup() {
        selectorManager = SelectorManager()
    }

    @AfterTest
    fun teardown() {
        selectorManager.close()
    }

    @Test
    fun testLimitSemaphore() = runTestWithRealTime {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        val sockets = mutableListOf<Socket>()
        withServerSocket { socket0 ->
            withServerSocket { socket1 ->
                withServerSocket { socket2 ->
                    sockets += connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                    sockets += connectionFactory.connect(socket1.localAddress as InetSocketAddress)

                    assertTimeout {
                        sockets += connectionFactory.connect(socket2.localAddress as InetSocketAddress)
                    }
                }
            }
        }

        sockets.forEach { it.close() }
    }

    @Test
    fun testAddressSemaphore() = runTestWithRealTime {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        val sockets = mutableListOf<Socket>()
        withServerSocket { socket0 ->

            withServerSocket { socket1 ->
                sockets += connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                assertTimeout {
                    sockets += connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                }

                sockets += connectionFactory.connect(socket1.localAddress as InetSocketAddress)
                assertTimeout {
                    sockets += connectionFactory.connect(socket1.localAddress as InetSocketAddress)
                }
            }
        }

        sockets.forEach { it.close() }
    }

    @Test
    fun testReleaseLimitSemaphoreWhenFailed() = runTestWithRealTime {
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 2,
            addressConnectionsLimit = 1,
        )
        val sockets = mutableListOf<Socket>()
        withServerSocket { socket0 ->
            withServerSocket { socket1 ->
                sockets += connectionFactory.connect(socket0.localAddress as InetSocketAddress)

                // Release the `limit` semaphore when it fails to acquire the address semaphore.
                assertTimeout {
                    sockets += connectionFactory.connect(socket0.localAddress as InetSocketAddress)
                }

                sockets += connectionFactory.connect(socket1.localAddress as InetSocketAddress)
            }
        }

        sockets.forEach { it.close() }
    }

    private suspend fun assertTimeout(timeoutMillis: Long = 500, block: suspend () -> Unit) {
        assertFailsWith(TimeoutCancellationException::class) {
            withTimeout(timeoutMillis) {
                block()
            }
        }
    }

    private suspend fun withServerSocket(block: suspend (ServerSocket) -> Unit) {
        aSocket(selectorManager).tcp().bind(TEST_SERVER_SOCKET_HOST, 0).use { socket ->
            block(socket)
        }
    }

    companion object {
        private const val TEST_SERVER_SOCKET_HOST = "127.0.0.1"
    }
}
