/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.network.tls.extensions.TLSExtension
import io.ktor.utils.io.core.*
import kotlin.experimental.*

// TODO
internal class TLSClientHello(
    val version: TLSVersion,
    val clientSeed: ByteArray,
    val sessionId: ByteArray,
    val suites: List<CipherSuite>,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = emptyList()
) {
    companion object: BytePacketSerializer<TLSClientHello> {
        override suspend fun read(input: ByteReadPacket): TLSClientHello {
            val version = TLSVersion.read(input)

            val random = ByteArray(32)
            input.readFully(random)
            val sessionIdLength = input.readByte().toInt() and 0xff

            if (sessionIdLength > 32) {
                throw TLSException("sessionId length limit of 32 bytes exceeded: $sessionIdLength specified")
            }

            val sessionId = ByteArray(32)
            input.readFully(sessionId, 0, sessionIdLength)

            val suites: List<CipherSuite> = buildList {
                for (i in 0 until input.readShort()) {
                    val suiteCode = input.readShort()
                    CIOCipherSuites.SupportedSuites.find { it.code == suiteCode }
                        ?.let(::add)
                }
            }

            val compressionMethod = input.readByte().toShort() and 0xff
            if (compressionMethod.toInt() != 0) {
                throw TLSException(
                    "Unsupported TLS compression method $compressionMethod (only null 0 compression method is supported)"
                )
            }

            val extensions = readTLSExtensions.read(input)

            return TLSClientHello(
                version,
                random,
                sessionId,
                suites,
                compressionMethod,
                extensions
            )
        }

        override fun write(output: BytePacketBuilder, value: TLSClientHello) {
            with(output) {
                writeShort(value.version.code.toShort())
                writeFully(value.clientSeed)

                val sessionIdLength = value.sessionId.size
                if (sessionIdLength < 0 || sessionIdLength > 0xff || sessionIdLength > value.sessionId.size) {
                    throw TLSException("Illegal sessionIdLength")
                }

                writeByte(sessionIdLength.toByte())
                writeFully(value.sessionId, 0, sessionIdLength)

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




}
