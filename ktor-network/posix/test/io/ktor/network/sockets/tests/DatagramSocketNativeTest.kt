/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
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
}
