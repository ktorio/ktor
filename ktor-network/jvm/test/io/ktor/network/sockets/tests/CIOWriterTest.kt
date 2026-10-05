/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class CIOWriterTest {

    @Test
    fun `writer does not loop when peer stops reading`() = runBlocking {
        val server = ServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0))
        val selector = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selector).tcp().connect("127.0.0.1", (server.localAddress as InetSocketAddress).port)
        server.accept()
        launch(Dispatchers.IO) {
            socket.openWriteChannel(autoFlush = true).writeFully(ByteArray(64 shl 20))
        }
        delay(1000.milliseconds)

        val threads = ManagementFactory.getThreadMXBean()
        val before = threads.allThreadIds.associateWith { threads.getThreadCpuTime(it) }
        delay(1000.milliseconds)

        var busiestId: Long? = null
        var busiestMs: Long = Long.MIN_VALUE
        for ((id, start) in before.entries) {
            if (busiestMs < threads.getThreadCpuTime(id) - start) {
                busiestMs = threads.getThreadCpuTime(id) - start
                busiestId = id
            }
        }

        busiestMs /= 1_000_000
        assertNotNull(busiestId)
        val threadName = threads.getThreadInfo(busiestId).threadName

        assertTrue(
            busiestMs < 200,
            "A thread $threadName burned ${busiestMs}ms CPU"
        )
    }
}
