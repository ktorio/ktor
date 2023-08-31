/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.integration

import io.ktor.network.quic.bytes.EMPTY_BYTE_ARRAY
import net.luminis.quic.*
import net.luminis.quic.log.*
import java.io.*
import java.net.*
import kotlin.test.*

class TestKwikClient(serverPort: Int) {
    private val log = SysOutLogger().apply {
        logPackets(true)
        logInfo(true)
    }

    private val uri = URI("http://localhost:$serverPort")

    private val connection = QuicClientConnection
        .newBuilder()
        .logger(log)
        .version(Version.QUIC_version_1)
        .noServerCertificateCheck()
        .uri(uri)
        .build()

    fun testConnection(messageSizeBytes: Int) {
        connection.connect(10_000, "hq-interop")

        val stream: QuicStream = connection.createStream(true)

        val message = ByteArray(messageSizeBytes) { it.toByte() }

        val outputStream = BufferedOutputStream(stream.outputStream)
        outputStream.write(message)
        outputStream.flush()

        val transferred = stream.inputStream.readAllBytes() ?: EMPTY_BYTE_ARRAY

        connection.close()

        assertContentEquals(message, transferred, "Response")
    }
}
