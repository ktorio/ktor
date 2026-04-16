/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.netty.http3.*
import io.netty.buffer.*
import java.net.*
import kotlin.test.*

class HmacQuicTokenHandlerTest {

    private val handler = HmacQuicTokenHandler()

    @Test
    fun `writeToken produces token with expected structure`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        val result = handler.writeToken(out, dcid, address)

        assertTrue(result, "writeToken should return true")
        // 8 bytes timestamp + 32 bytes HMAC + 8 bytes dcid
        assertEquals(48, out.readableBytes())

        out.release()
        dcid.release()
    }

    @Test
    fun `valid token is accepted and returns correct dcid offset`() {
        val out = Unpooled.buffer()
        val dcidBytes = ByteArray(8) { it.toByte() }
        val dcid = Unpooled.wrappedBuffer(dcidBytes)
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        handler.writeToken(out, dcid, address)

        val offset = handler.validateToken(out, address)
        // offset should point to where dcid starts (after timestamp + HMAC)
        assertEquals(40, offset)

        // Verify dcid bytes are accessible at the returned offset
        val extractedDcid = ByteArray(out.readableBytes() - offset)
        out.getBytes(out.readerIndex() + offset, extractedDcid)
        assertContentEquals(dcidBytes, extractedDcid)

        out.release()
        dcid.release()
    }

    @Test
    fun `token from different address is rejected`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val originalAddress = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)
        val differentAddress = InetSocketAddress(InetAddress.getByName("192.168.1.1"), 12345)

        handler.writeToken(out, dcid, originalAddress)

        val offset = handler.validateToken(out, differentAddress)
        assertEquals(-1, offset, "Token from different address should be rejected")

        out.release()
        dcid.release()
    }

    @Test
    fun `token from different port is rejected`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val originalAddress = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)
        val differentPort = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 54321)

        handler.writeToken(out, dcid, originalAddress)

        val offset = handler.validateToken(out, differentPort)
        assertEquals(-1, offset, "Token from different port should be rejected")

        out.release()
        dcid.release()
    }

    @Test
    fun `expired token is rejected`() {
        val shortLivedHandler = HmacQuicTokenHandler(
            keyGen = HmacQuicTokenHandler::generateDefaultKey,
            tokenLifetimeMillis = 1
        )

        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        shortLivedHandler.writeToken(out, dcid, address)

        Thread.sleep(10)

        val offset = shortLivedHandler.validateToken(out, address)
        assertEquals(-1, offset, "Expired token should be rejected")

        out.release()
        dcid.release()
    }

    @Test
    fun `forged token is rejected`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        handler.writeToken(out, dcid, address)

        // Corrupt one byte of the HMAC (after the 8-byte timestamp)
        val hmacStart = out.readerIndex() + 8
        val original = out.getByte(hmacStart)
        out.setByte(hmacStart, (original.toInt() xor 0xFF).toByte().toInt())

        val offset = handler.validateToken(out, address)
        assertEquals(-1, offset, "Forged token should be rejected")

        out.release()
        dcid.release()
    }

    @Test
    fun `truncated token is rejected`() {
        val token = Unpooled.wrappedBuffer(ByteArray(5))
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        val offset = handler.validateToken(token, address)
        assertEquals(-1, offset, "Truncated token should be rejected")

        token.release()
    }

    @Test
    fun `token with different key is rejected`() {
        val handler1 = HmacQuicTokenHandler()
        val handler2 = HmacQuicTokenHandler()

        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        handler1.writeToken(out, dcid, address)

        val offset = handler2.validateToken(out, address)
        assertEquals(-1, offset, "Token signed with different key should be rejected")

        out.release()
        dcid.release()
    }

    @Test
    fun `maxTokenLength accounts for max connection id`() {
        // 8 bytes timestamp + 32 bytes HMAC + MAX_CONN_ID_LEN
        assertTrue(handler.maxTokenLength() >= 40)
    }

    @Test
    fun `valid token with IPv6 address`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("::1"), 12345)

        handler.writeToken(out, dcid, address)

        val offset = handler.validateToken(out, address)
        assertTrue(offset >= 0, "Valid IPv6 token should be accepted")

        out.release()
        dcid.release()
    }

    @Test
    fun `tampered dcid in token is rejected`() {
        val out = Unpooled.buffer()
        val dcid = Unpooled.wrappedBuffer(ByteArray(8) { it.toByte() })
        val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345)

        handler.writeToken(out, dcid, address)

        // Corrupt a byte in the dcid portion (after timestamp + HMAC)
        val dcidStart = out.readerIndex() + 40
        val original = out.getByte(dcidStart)
        out.setByte(dcidStart, (original.toInt() xor 0xFF).toByte().toInt())

        val offset = handler.validateToken(out, address)
        assertEquals(-1, offset, "Token with tampered dcid should be rejected")

        out.release()
        dcid.release()
    }
}
