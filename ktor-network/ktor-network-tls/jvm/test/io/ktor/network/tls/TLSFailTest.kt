/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.Test

const val PORT = 7654

class TLSFailTest {

    @Test
    fun test() = runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)

        val serverSocket = aSocket(selectorManager)
            .tcp()
            .bind("0.0.0.0", port = PORT)

        val serverJob = launch {
            val socket = serverSocket.accept()
            val readChannel = socket.openReadChannel()
            /*
            1. Read client hello.
            2. Send server hello
            3. Send handshakes
              - Certificate
              - CertificateRequest
              - ServerKeyExchange
              - ServerDone
             */
            val clientHelloPacket = readChannel.readPacket(32)
            val writeChannel = socket.openWriteChannel(autoFlush = true)
//            sendHandshakeRecord(TLSHandshakeType.ServerHello) {
//
//            }

            // socket.close()
        }

        val clientJob = launch {
            val clientSocket = aSocket(selectorManager)
                .tcp()
                .connect("0.0.0.0", port = PORT)
                .tls(Dispatchers.Default)

            val channel = clientSocket.openWriteChannel()

            channel.apply {
                writeStringUtf8("GET / HTTP/1.1\r\n")
                writeStringUtf8("Host: 0.0.0.0\r\n")
                writeStringUtf8("Connection: close\r\n\r\n")
                flush()
            }

            clientSocket.openReadChannel().readRemaining()
        }

        joinAll(serverJob, clientJob)
    }

//    internal fun BytePacketBuilder.writeTLSServerHello(
//        version: TLSVersion,
//        suites: List<CipherSuite>,
//        random: ByteArray,
//        sessionId: ByteArray,
//        serverName: String? = null
//    ) {
//        writeShort(version.code.toShort())
//        writeFully(random)
//
//        val sessionIdLength = sessionId.size
//        if (sessionIdLength < 0 || sessionIdLength > 0xff || sessionIdLength > sessionId.size) {
//            throw TLSException("Illegal sessionIdLength")
//        }
//
//        writeByte(sessionIdLength.toByte())
//        writeFully(sessionId, 0, sessionIdLength)
//
//        writeShort((suites.size * 2).toShort())
//        for (suite in suites) {
//            writeShort(suite.code)
//        }
//
//        // compression is always null
//        writeByte(1)
//        writeByte(0)
//
//        val extensions = ArrayList<ByteReadPacket>()
//        extensions += buildSignatureAlgorithmsExtension()
//        extensions += buildECCurvesExtension()
//        extensions += buildECPointFormatExtension()
//
//        serverName?.let { name ->
//            extensions += buildServerNameExtension(name)
//        }
//
//        writeShort(extensions.sumOf { it.remaining.toInt() }.toShort())
//        for (e in extensions) {
//            writePacket(e)
//        }
//    }

}
