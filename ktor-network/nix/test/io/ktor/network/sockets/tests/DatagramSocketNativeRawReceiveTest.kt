/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.cinterop.*
import kotlinx.io.*
import platform.posix.sendto
import kotlin.test.*
import kotlin.use

@OptIn(ExperimentalForeignApi::class)
class DatagramSocketNativeRawReceiveTest {

    @Test
    fun testEmptyDatagramKeepsReceiverOpen() = testSockets { selector ->
        aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { server ->
            aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { client ->
                val descriptor = (client as DatagramSocketNative).descriptor
                val payload = byteArrayOf(42)

                fun rawSend(length: Int) {
                    payload.usePinned { pinned ->
                        server.localAddress.address.nativeAddress { address, addressSize ->
                            val sent = sendto(
                                descriptor,
                                pinned.addressOf(0),
                                length.convert(),
                                0,
                                address,
                                addressSize.convert()
                            )
                            assertEquals(length.toLong(), sent.toLong(), "raw sendto failed")
                        }
                    }
                }

                rawSend(0)

                val result = server.incoming.receiveCatching()
                assertFalse(result.isClosed, "incoming closed: ${result.exceptionOrNull()}")

                val empty = result.getOrThrow()
                assertTrue(empty.packet.exhausted(), "expected an empty datagram")
                assertEquals(client.localAddress, empty.address)

                rawSend(payload.size)

                val next = server.receive()
                assertContentEquals(payload, next.packet.readByteArray())
                assertEquals(client.localAddress, next.address)
            }
        }
    }
}
