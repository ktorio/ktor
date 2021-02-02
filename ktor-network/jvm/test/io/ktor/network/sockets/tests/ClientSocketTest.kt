/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import org.junit.rules.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*
import kotlin.test.Test

class ClientSocketTest {
    private val exec = Executors.newCachedThreadPool()
    private val selector = ActorSelectorManager(exec.asCoroutineDispatcher())
    private var server: Pair<ServerSocket, Thread>? = null

    @get:Rule
    val timeout = CoroutinesTimeout.seconds(600)

    @get:Rule
    val errors = ErrorCollector()

    @AfterTest
    fun tearDown() {
        server?.let { (server, thread) ->
            server.close()
            thread.interrupt()
        }
        selector.close()
        exec.shutdown()
    }

    @Test
    fun testConnect() {
        server { it.close() }

        client {
        }
    }

    @Test
    fun testRead() {
        server { client ->
            client.getOutputStream().use { o ->
                o.write("123".toByteArray())
                o.flush()
            }
        }

        client { socket ->
            val bb = ByteBuffer.allocate(3)
            val channel = socket.openReadChannel()
            channel.readFully(bb)
            assertEquals("123", String(bb.array()))
        }
    }

    @Test
    fun testWrite() {
        server { client ->
            assertEquals("123", client.getInputStream().reader().readText())
        }

        client { socket ->
            val channel = socket.openWriteChannel(true)
            channel.writeStringUtf8("123")
        }
    }

    @Test
    fun testReadParts() {
        server { client ->
            client.getOutputStream().use { o ->
                o.write("0123456789".toByteArray())
                o.flush()
            }
        }

        client { socket ->
            assertEquals("0123456789", socket.openReadChannel().readUTF8Line())
        }
    }

    @Test
    fun testSelfConnect() {
        runBlocking {
            // Find a port that would be used as a local address.
            val port = getAvailablePort()

            val tcpSocketBuilder = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
            // Try to connect to that address repeatedly.
            for (i in 0 until 100000) {
                try {
                    val socket = tcpSocketBuilder.connect(InetSocketAddress("127.0.0.1", port))
                    fail("connect to self succeed: ${socket.localAddress} to ${socket.remoteAddress}")
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    // since new linux kernel version introduce a feature, new bind port number will always be odd number
    // and connect port will always be even, so we find a random even port with while loop
    // see https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/commit/?id=07f4c90062f8fc7c8c26f8f95324cbe8fa3145a5
    private fun getAvailablePort(): Int {
        while (true) {
            val port = ServerSocket().apply {
                bind(InetSocketAddress("127.0.0.1", 0))
                close()
            }.localPort

            if (port % 2 == 0) {
                return port
            }

            try {
                // try bind the next even port
                ServerSocket().apply {
                    bind(InetSocketAddress("127.0.0.1", port + 1))
                    close()
                }
                return port + 1
            } catch (ex: Exception) {
                // ignore
            }
        }
    }

    private fun client(block: suspend (Socket) -> Unit) {
        runBlocking {
            aSocket(selector).tcp().connect(server!!.first.localSocketAddress).use {
                block(it)
            }
        }
    }

    private fun server(block: (java.net.Socket) -> Unit) {
        val server = ServerSocket(0)
        val thread = thread(start = false) {
            try {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (t: Throwable) {
                        break
                    }

                    client.use(block)
                }
            } catch (t: Throwable) {
                errors.addError(t)
            }
        }

        this.server = Pair(server, thread)
        thread.start()
    }
}
