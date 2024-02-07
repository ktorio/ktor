/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*
import kotlin.experimental.*

internal class TLSServerHello(
    val version: TLSVersion,
    val serverSeed: ByteArray,
    val sessionId: ByteArray,
    val cipherSuite: CipherSuite,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = emptyList()
) {
    companion object: BytePacketSerializer<TLSServerHello> {

        override suspend fun read(input: ByteReadPacket): TLSServerHello {
            val version = TLSVersion.read(input)

            val random = ByteArray(32)
            input.readFully(random)
            val sessionIdLength = input.readByte().toInt() and 0xff

            if (sessionIdLength > 32) {
                throw TLSException("sessionId length limit of 32 bytes exceeded: $sessionIdLength specified")
            }

            val sessionId = ByteArray(32)
            input.readFully(sessionId, 0, sessionIdLength)

            val suite = input.readShort()

            val compressionMethod = input.readByte().toShort() and 0xff
            if (compressionMethod.toInt() != 0) {
                throw TLSException(
                    "Unsupported TLS compression method $compressionMethod (only null 0 compression method is supported)"
                )
            }

            return TLSServerHello(version, random, sessionId, suite, compressionMethod, readTLSExtensions.read(input))
        }

        override fun write(output: BytePacketBuilder, value: TLSServerHello) = with(output) {
            // version
            writeShort(value.version.code.toShort())

            // random
            writeFully(value.serverSeed)

            // sessionId
            writeByte(value.sessionId.size.toByte())
            writeFully(value.sessionId)

            // cipher suite
            writeShort(value.cipherSuite.code)

            // compression method, always null
            writeByte(1)
            writeByte(0)

            // extensions
            writeShort(value.extensions.sumOf { it.length }.toShort())
            for (e in value.extensions)
                writePacket(e.packet)
        }

    }

    constructor(
        version: TLSVersion,
        serverSeed: ByteArray,
        sessionId: ByteArray,
        cipherCode: Short,
        compressionMethod: Short,
        extensions: List<TLSExtension>
    ) : this(
        version,
        serverSeed,
        sessionId,
        CIOCipherSuites.SupportedSuites.find { it.code == cipherCode }
            ?: error("Server cipher suite is not supported: $cipherCode"),
        compressionMethod,
        extensions
    )

    val hashAndSignAlgorithms: List<HashAndSign> by lazy {
        extensions.flatMap {
            if (it.type != TLSExtensionType.SIGNATURE_ALGORITHMS)
                emptyList()
            else
                it.packet.parseSignatureAlgorithms()
        }
    }
}

