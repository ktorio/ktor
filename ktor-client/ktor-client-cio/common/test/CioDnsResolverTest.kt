/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.*
import io.ktor.utils.io.core.buildPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.use

class CioDnsResolverTest {

    private fun runTestWithNoDelaySkipping(testBody: suspend CoroutineScope.() -> Unit): TestResult = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            testBody()
        }
    }

    @Test
    fun testCioDnsResolverParsesAResponse() = runTestWithNoDelaySkipping {
        withFakeDnsServer { server, port ->
            val resolved = async {
                CioDnsResolver(server = "127.0.0.1", port = port, timeout = 2.seconds)
                    .invoke("example.com")
            }
            server.replyWithA("1.2.3.4")
            server.replyEmpty() // NODATA for AAAA so the resolver returns immediately
            assertEquals(listOf("1.2.3.4"), resolved.await())
        }
    }

    @Test
    fun testCioDnsResolverReturnsAaaaOnlyForIpv6OnlyHost() = runTestWithNoDelaySkipping {
        withFakeDnsServer { server, port ->
            val resolved = async {
                CioDnsResolver(server = "127.0.0.1", port = port, timeout = 2.seconds)
                    .invoke("ipv6.example")
            }
            server.replyEmpty() // NODATA for A
            server.replyWithAaaa(byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte()) + ByteArray(11) + byteArrayOf(2))
            assertEquals(listOf("2001:db8::2"), resolved.await())
        }
    }

    @Test
    fun testCioDnsResolverReturnsEmptyOnNxDomain() = runTestWithNoDelaySkipping {
        withFakeDnsServer { server, port ->
            val resolved = async {
                CioDnsResolver(server = "127.0.0.1", port = port, timeout = 2.seconds)
                    .invoke("nonexistent.example")
            }
            server.replyNxDomain()
            server.replyNxDomain()
            assertEquals(emptyList(), resolved.await())
        }
    }

    @Test
    fun testCioDnsResolverHappyEyeballsPrefersIpv6() = runTestWithNoDelaySkipping {
        withFakeDnsServer { server, port ->
            val resolved = async {
                CioDnsResolver(server = "127.0.0.1", port = port, timeout = 2.seconds)
                    .invoke("dual.example")
            }
            server.replyWithA("203.0.113.1")
            server.replyWithAaaa(byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte()) + ByteArray(11) + byteArrayOf(1))
            assertEquals(listOf("2001:db8::1", "203.0.113.1"), resolved.await())
        }
    }

    @Test
    fun testCioDnsResolverThrowsWhenAllQueriesTimeOut() = runTestWithNoDelaySkipping {
        SelectorManager().use { selector ->
            aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { server ->
                val port = (server.localAddress as InetSocketAddress).port
                assertFailsWith<IOException> {
                    CioDnsResolver(
                        server = "127.0.0.1",
                        port = port,
                        timeout = 200.milliseconds,
                    ).invoke("slow.example")
                }
            }
        }
    }

    @Test
    fun testCioDnsResolverRejectsEmptyHostname() = runTestWithNoDelaySkipping {
        val failure = assertFailsWith<IllegalArgumentException> {
            CioDnsResolver(server = "127.0.0.1").invoke("")
        }
        assertContains(failure.message ?: "", "hostname")
    }

    @Test
    fun testCioDnsResolverAcceptsFqdnWithTrailingDot() = runTestWithNoDelaySkipping {
        withFakeDnsServer { server, port ->
            val resolved = async {
                CioDnsResolver(server = "127.0.0.1", port = port, timeout = 2.seconds)
                    .invoke("example.com.")
            }
            server.replyWithA("203.0.113.99")
            server.replyEmpty()
            assertEquals(listOf("203.0.113.99"), resolved.await())
        }
    }

    @Test
    fun testFormatIpv6CompressesLongestZeroRun() {
        assertEquals("2001:db8::1", formatIpv6(intArrayOf(0x2001, 0x0db8, 0, 0, 0, 0, 0, 1)))
        assertEquals("::1", formatIpv6(intArrayOf(0, 0, 0, 0, 0, 0, 0, 1)))
        assertEquals("::", formatIpv6(IntArray(8)))
        assertEquals("1::1", formatIpv6(intArrayOf(1, 0, 0, 0, 0, 0, 0, 1)))
        // Single zero groups are not compressed (RFC 5952 §4.2.2)
        assertEquals("1:0:1:0:1:0:1:0", formatIpv6(intArrayOf(1, 0, 1, 0, 1, 0, 1, 0)))
    }

    private suspend fun withFakeDnsServer(
        block: suspend (FakeDnsServer, Int) -> Unit,
    ) {
        SelectorManager().use { selector ->
            aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0)).use { socket ->
                val port = (socket.localAddress as InetSocketAddress).port
                coroutineScope { block(FakeDnsServer(socket), port) }
            }
        }
    }

    private class FakeDnsServer(private val socket: BoundDatagramSocket) {
        suspend fun replyWithA(ipv4: String) {
            val octets = ipv4.split('.').map { it.toInt().toByte() }.toByteArray()
            require(octets.size == 4)
            replyWithRecord(typeA = true, rdata = octets)
        }

        suspend fun replyWithAaaa(rawIpv6: ByteArray) {
            require(rawIpv6.size == 16) { "AAAA RDATA must be 16 bytes" }
            replyWithRecord(typeA = false, rdata = rawIpv6)
        }

        suspend fun replyEmpty() = sendHeaderOnlyResponse(rcode = 0)

        suspend fun replyNxDomain() = sendHeaderOnlyResponse(rcode = 3)

        private suspend fun sendHeaderOnlyResponse(rcode: Int) {
            val request = socket.receive()
            val requestBytes = request.packet.readByteArray()
            val transactionId = ((requestBytes[0].toInt() and 0xFF) shl 8) or (requestBytes[1].toInt() and 0xFF)
            val questionSection = requestBytes.copyOfRange(12, requestBytes.size)
            val response = buildPacket {
                writeShort(transactionId.toShort())
                writeShort((0x8180 or rcode).toShort()) // QR=1, RD=1, RA=1, low nibble = RCODE
                writeShort(1) // QDCOUNT
                writeShort(0) // ANCOUNT
                writeShort(0) // NSCOUNT
                writeShort(0) // ARCOUNT
                write(questionSection)
            }
            socket.send(Datagram(response, request.address))
        }

        private suspend fun replyWithRecord(typeA: Boolean, rdata: ByteArray) {
            val request = socket.receive()
            val requestBytes = request.packet.readByteArray()
            val transactionId = ((requestBytes[0].toInt() and 0xFF) shl 8) or (requestBytes[1].toInt() and 0xFF)
            val questionSection = requestBytes.copyOfRange(12, requestBytes.size)

            val response = buildPacket {
                writeShort(transactionId.toShort())
                writeShort(0x8180.toShort()) // QR=1, RD=1, RA=1, RCODE=0
                writeShort(1) // QDCOUNT
                writeShort(1) // ANCOUNT
                writeShort(0) // NSCOUNT
                writeShort(0) // ARCOUNT
                write(questionSection) // copy original question
                writeShort(0xC00C.toShort()) // NAME pointer to QNAME at offset 12
                writeShort(if (typeA) 1 else 28) // TYPE A or AAAA
                writeShort(1) // CLASS IN
                writeInt(60) // TTL
                writeShort(rdata.size.toShort())
                write(rdata)
            }
            socket.send(Datagram(response, request.address))
        }
    }
}
