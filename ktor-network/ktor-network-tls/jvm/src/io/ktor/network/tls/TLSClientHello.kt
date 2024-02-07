/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.TLSExtension
import io.ktor.utils.io.core.*
import kotlin.experimental.*

internal class TLSClientHello(
    val header: TLSHelloHeader,
    val suites: List<CipherSuite>,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = emptyList()
) {
    companion object : BytePacketSerializer<TLSClientHello> {
        override suspend fun read(input: ByteReadPacket): TLSClientHello {
            val header = TLSHelloHeader.read(input)

            val suites: List<CipherSuite> = buildList {
                val size = input.readShort()
                var i = 0
                while (i < size) {
                    val suiteCode = input.readShort()
                    CIOCipherSuites.SupportedSuites.find { it.code == suiteCode }
                        ?.let(::add)
                    i += 2
                }
            }

            val compressionMethod = input.readShort() and 0xff
            if (compressionMethod.toInt() != 0) {
                throw TLSUnsupportedException(
                    "Unsupported TLS compression method $compressionMethod (only null 0 compression method is supported)"
                )
            }

            val extensions = readTLSExtensions.read(input)

            return TLSClientHello(header, suites, compressionMethod, extensions)
        }

        override fun write(output: BytePacketBuilder, value: TLSClientHello) {
            with(output) {
                TLSHelloHeader.write(output, value.header)

                writeShort((value.suites.size * 2).toShort())
                for (suite in value.suites) {
                    writeShort(suite.code)
                }

                // compression is always null
                writeByte(1)
                writeByte(0)

                writeShort(value.extensions.sumOf { it.length }.toShort())
                for (e in value.extensions) {
                    writePacket(e.packet)
                }
            }
        }
    }

    val version: TLSVersion = header.version
    val clientSeed: ByteArray = header.seed
    val sessionId: ByteArray = header.sessionId
}
