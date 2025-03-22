/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.junit.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@CoroutinesTimeout(5 * 60 * 1000)
@ErrorCollectorTest
class ClientSocketTest {
    private val exec = Executors.newCachedThreadPool()
    private val selector = ActorSelectorManager(exec.asCoroutineDispatcher())
    private var server: Pair<ServerSocket, Thread>? = null

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
    fun testConnect(errors: ErrorCollector) {
        server(errors) { it.close() }

        client {
        }
    }

    @Test
    fun testRead(errors: ErrorCollector) {
        server(errors) { client ->
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
    fun testWrite(errors: ErrorCollector) {
        server(errors) { client ->
            assertEquals("123", client.getInputStream().reader().readText())
        }

        client { socket ->
            val channel = socket.openWriteChannel(true)
            channel.writeStringUtf8("123")
        }
    }

    @Test
    fun testReadParts(errors: ErrorCollector) {
        server(errors) { client ->
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
        val selector = mockk<SelectorManager>()
        coEvery { selector.select(any(), any()) } just Runs

        val channel = mockk<SocketChannel>()
        every { channel.socket() } returns mockSocket(
            local = mockSocketAddress("client", 1),
            remote = mockSocketAddress("client", 1),
            channel = channel
        )
        every { channel.isBlocking } returns false
        every { channel.connect(any()) } returns false
        every { channel.isOpen } returns true
        every { channel.finishConnect() } answers {
            if (!channel.isOpen) throw ClosedChannelException()
            true
        }
        every { channel.close() } answers {
            every { channel.isOpen } returns false
        }

        runBlocking {
            assertFailsWith<ClosedChannelException>(
                "Channel should be closed if local and remote addresses of client socket match"
            ) {
                SocketImpl(channel, selector).connect(
                    mockSocketAddress("server", 2)
                )
            }
        }
    }

    private fun mockSocket(local: SocketAddress, remote: SocketAddress, channel: SocketChannel): java.net.Socket {
        val socket = mockk<java.net.Socket>()
        every { channel.localAddress } returns local
        every { channel.remoteAddress } returns remote
        every { socket.close() } answers { channel.close() }
        return socket
    }

    private fun mockSocketAddress(hostAddress: String, port: Int): InetSocketAddress {
        val address = mockk<InetSocketAddress>()
        every { address.port } returns port
        every { address.address?.hostAddress } returns hostAddress
        every { address.address?.isAnyLocalAddress } returns false
        return address
    }

    private fun client(block: suspend (Socket) -> Unit) {
        runBlocking {
            aSocket(selector).tcp().connect(server!!.first.localSocketAddress.toSocketAddress()).use {
                block(it)
            }
        }
    }

    private fun server(errors: ErrorCollector, block: (java.net.Socket) -> Unit) {
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
                errors += t
            }
        }

        this.server = Pair(server, thread)
        thread.start()
    }
}
