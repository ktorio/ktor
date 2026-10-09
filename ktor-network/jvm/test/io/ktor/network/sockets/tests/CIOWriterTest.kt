/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CIOWriterTest {

    @Test
    fun `writer does not loop when peer stops reading`() = runBlocking {
        val server = ServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0))
        val selector = SelectorManager(Dispatchers.IO)

        val clientChannel = SocketChannel.open()
        clientChannel.configureBlocking(false)
        clientChannel.connect(InetSocketAddress("127.0.0.1", (server.localAddress as InetSocketAddress).port))
        while (!clientChannel.finishConnect()) {
            // Wait for the loopback connection to complete.
        }

        val accepted = server.accept()
        accepted.configureBlocking(true)

        val writeAttempts = AtomicInteger(0)
        val backpressureStarted = CompletableDeferred<Unit>()
        val countingChannel = object : WritableByteChannel {
            override fun isOpen(): Boolean = clientChannel.isOpen
            override fun close() = clientChannel.close()
            override fun write(src: ByteBuffer): Int {
                writeAttempts.incrementAndGet()
                val rc = clientChannel.write(src)
                if (rc == 0) {
                    backpressureStarted.complete(Unit)
                }
                return rc
            }
        }

        val socket = SocketImpl(clientChannel, selector)
        val byteChannel = ByteChannel(autoFlush = true)
        val writerJob = attachForWritingDirectImpl(byteChannel, countingChannel, socket, selector)

        val producer = launch(Dispatchers.IO) {
            byteChannel.writeFully(ByteArray(64 shl 20))
        }

        try {
            withTimeout(10.seconds) {
                backpressureStarted.await()
            }
            val attemptsAfterWarmup = writeAttempts.get()

            val pollInterval = 20.milliseconds
            val pollCount = 25 // 500ms total upper bound
            repeat(pollCount) {
                delay(pollInterval)
                val attemptsDuringBackpressure = writeAttempts.get() - attemptsAfterWarmup
                assertTrue(
                    attemptsDuringBackpressure < 1000,
                    "Writer attempted $attemptsDuringBackpressure writes while the peer was " +
                        "not reading, indicating a busy loop"
                )
            }
        } finally {
            producer.cancel()
            writerJob.cancel()
            byteChannel.cancel()
            socket.close()
            accepted.close()
            server.close()
            selector.close()
        }
    }
}
