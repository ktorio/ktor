/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*
import kotlin.experimental.*

internal class TLSServerHello(
    val header: TLSHelloHeader,
    val cipherSuite: CipherSuite,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = emptyList()
) {

    companion object : BytePacketSerializer<TLSServerHello> {

        override suspend fun read(input: ByteReadPacket): TLSServerHello {
            val header = TLSHelloHeader.read(input)
            val suite = input.readShort()
            val compressionMethod = input.readByte().toShort() and 0xff
            if (compressionMethod.toInt() != 0) {
                throw TLSValidationException(
                    "Unsupported TLS compression method $compressionMethod (only null 0 compression method is supported)"
                )
            }
            return TLSServerHello(
                header = header,
                cipherSuite = CIOCipherSuites.SupportedSuites.find {
                    it.code == suite
                } ?: error("Server cipher suite is not supported: $suite"),
                compressionMethod = compressionMethod,
                extensions = readTLSExtensions.read(input)
            )
        }

        override fun write(output: BytePacketBuilder, value: TLSServerHello) = with(output) {
            TLSHelloHeader.write(output, value.header)

            // cipher suite
            writeShort(value.cipherSuite.code)

            // compression method, always null
            writeByte(0)

            // extensions
            writeTLSExtensions.write(this, value.extensions)
        }
    }

    val version: TLSVersion = header.version
    val serverSeed: ByteArray = header.seed
    val sessionId: ByteArray = header.sessionId

    val hashAndSignAlgorithms: List<HashAndSign> by lazy {
        extensions.flatMap {
            if (it.type != TLSExtensionType.SIGNATURE_ALGORITHMS) {
                emptyList()
            } else
                it.packet.parseSignatureAlgorithms()
        }
    }
}
