/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.util.cio.*
import io.ktor.utils.io.pool.useInstance
import io.netty.buffer.*
import io.netty.handler.codec.quic.*
import java.net.*
import java.nio.*
import java.security.*
import javax.crypto.*

/**
 * Maximum age of a valid token in milliseconds (default: 60 seconds).
 */
private const val TOKEN_LIFETIME_MS = 60_000L

/**
 * Length of the HMAC-SHA256 output in bytes.
 */
private const val HMAC_LENGTH = 32

/**
 * Length of the timestamp in bytes (Long = 8 bytes).
 */
private const val TIMESTAMP_LENGTH = 8

/**
 * A secure [QuicTokenHandler] that generates and validates QUIC retry tokens
 * using HMAC-SHA256. Tokens bind the client's address and port to a timestamp
 * and are cryptographically signed to prevent forgery. Expired tokens are
 * rejected to mitigate replay attacks.
 *
 * Token format:
 * ```
 * [timestamp (8 bytes)] [HMAC-SHA256 (32 bytes)] [dcid (variable)]
 * ```
 *
 * The HMAC is computed over:
 * ```
 * [timestamp (8 bytes)] [address bytes] [port (4 bytes)] [dcid bytes]
 * ```
 *
 * The destination connection id is appended after the HMAC so that the QUIC
 * implementation can extract it at the offset returned by [validateToken].
 *
 * @param keyGen the secret key used for HMAC signing and validation.
 *   If not provided, a random 256-bit key is generated.
 * @param tokenLifetimeMillis maximum age of a valid token in milliseconds.
 */
internal class HmacQuicTokenHandler(
    keyGen: () -> SecretKey = ::generateDefaultKey,
    private val tokenLifetimeMillis: Long = TOKEN_LIFETIME_MS,
) : QuicTokenHandler {

    private val secretKey: SecretKey by lazy(keyGen)

    override fun writeToken(out: ByteBuf, dcid: ByteBuf, address: InetSocketAddress): Boolean {
        val timestamp = System.currentTimeMillis()

        KtorDefaultPool.useInstance { dcidBuffer ->
            dcidBuffer.clear()
            dcidBuffer.limit(dcid.readableBytes())
            dcid.getBytes(dcid.readerIndex(), dcidBuffer.array(), 0, dcid.readableBytes())

            val mac = computeHmac(timestamp, address, dcidBuffer)

            out.writeLong(timestamp)
            out.writeBytes(mac)
            out.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes())
        }

        return true
    }

    override fun validateToken(token: ByteBuf, address: InetSocketAddress): Int {
        val readable = token.readableBytes()
        val headerLength = TIMESTAMP_LENGTH + HMAC_LENGTH
        if (readable < headerLength) return -1

        val timestamp = token.getLong(token.readerIndex())

        val now = System.currentTimeMillis()
        if (now - timestamp > tokenLifetimeMillis || timestamp > now) return -1

        val receivedMac = ByteArray(HMAC_LENGTH)
        token.getBytes(token.readerIndex() + TIMESTAMP_LENGTH, receivedMac)

        val dcidLength = readable - headerLength
        if (dcidLength !in 0..Quic.MAX_CONN_ID_LEN) return -1

        val expectedMac = KtorDefaultPool.useInstance { dcidBuffer ->
            dcidBuffer.clear()
            dcidBuffer.limit(dcidLength)
            token.getBytes(token.readerIndex() + headerLength, dcidBuffer.array(), 0, dcidLength)
            computeHmac(timestamp, address, dcidBuffer)
        }

        if (!MessageDigest.isEqual(receivedMac, expectedMac)) return -1

        return headerLength
    }

    override fun maxTokenLength(): Int = TIMESTAMP_LENGTH + HMAC_LENGTH + Quic.MAX_CONN_ID_LEN

    private fun computeHmac(timestamp: Long, address: InetSocketAddress, dcidBytes: ByteBuffer): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)

        KtorDefaultPool.useInstance { timestampBuffer ->
            timestampBuffer.limit(TIMESTAMP_LENGTH)
            timestampBuffer.putLong(0, timestamp)
            timestampBuffer.position(0)
            timestampBuffer.limit(TIMESTAMP_LENGTH)

            mac.update(timestampBuffer)

            KtorDefaultPool.useInstance { portBuffer ->
                portBuffer.clear()
                portBuffer.limit(4)

                val port = address.port
                portBuffer.put(0, (port shr 24 and 0xFF).toByte())
                portBuffer.put(1, (port shr 16 and 0xFF).toByte())
                portBuffer.put(2, (port shr 8 and 0xFF).toByte())
                portBuffer.put(3, (port and 0xFF).toByte())
                portBuffer.position(0)
                portBuffer.limit(4)

                mac.update(address.address.address)
                mac.update(portBuffer)
                mac.update(dcidBytes)

                return mac.doFinal()
            }
        }
    }

    internal companion object {
        internal fun generateDefaultKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("HmacSHA256")
            keyGen.init(256, SecureRandom())
            return keyGen.generateKey()
        }
    }
}
