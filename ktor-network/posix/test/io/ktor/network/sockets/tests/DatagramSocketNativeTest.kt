/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.io.IOException
import kotlinx.io.readString
import kotlin.test.*
import kotlin.use

class DatagramSocketNativeTest {

    @Test
    fun testEmptyDatagramKeepsSocketOpen() = testSockets { selector ->
        aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { server ->
            aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { client ->
                client.send(Datagram(buildPacket { }, server.localAddress))

                val empty = server.receive()
                assertTrue(empty.packet.exhausted(), "expected an empty datagram")
                assertEquals(client.localAddress, empty.address)

                client.send(
                    Datagram(buildPacket { writeText("hello") }, server.localAddress)
                )
                assertEquals("hello", server.receive().packet.readString())
            }
        }
    }

    @Test
    fun testTrySendEmptyDatagramKeepsSocketOpen() = testSockets { selector ->
        aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { server ->
            aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { client ->
                val result = client.outgoing.trySend(
                    Datagram(buildPacket { }, server.localAddress)
                )
                assertTrue(result.isSuccess, "empty datagram was not accepted")

                val empty = server.receive()
                assertTrue(empty.packet.exhausted(), "expected an empty datagram")
                assertEquals(client.localAddress, empty.address)

                client.send(
                    Datagram(buildPacket { writeText("hello") }, server.localAddress)
                )
                assertEquals("hello", server.receive().packet.readString())
            }
        }
    }

    @Test
    fun `sending fails without touching the descriptor once it is closed`() = testSockets { selector ->
        aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { server ->
            val client = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)) as DatagramSocketNative
            // The socket's own channel rejects sends after close before they reach the descriptor.
            val channel = DatagramSendChannel(client.descriptor, client, remote = null)
            val payloads = mapOf(
                "empty" to ByteArray(0),
                "small" to ByteArray(16),
                "larger than one buffer segment" to ByteArray(32 * 1024),
            )

            client.close()
            client.awaitClosed()

            for ((name, payload) in payloads) {
                val sendFailure = assertFailsWith<IOException>("send, $name") {
                    channel.send(Datagram(buildPacket { writeFully(payload) }, server.localAddress))
                }
                assertEquals("Socket closed", sendFailure.message, "send, $name")

                val trySendFailure = assertFailsWith<IOException>("trySend, $name") {
                    channel.trySend(Datagram(buildPacket { writeFully(payload) }, server.localAddress))
                }
                assertEquals("Socket closed", trySendFailure.message, "trySend, $name")
            }
        }
    }
}
