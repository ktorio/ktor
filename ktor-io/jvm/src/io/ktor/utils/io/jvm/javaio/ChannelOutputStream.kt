/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

@InternalAPI
@OptIn(DelicateCoroutinesApi::class)
public open class ChannelOutputStream(
    private val channel: ByteWriteChannel,
    coroutineContext: CoroutineContext = Dispatchers.IO,
) : OutputStream() {
    private val scope = CoroutineScope(coroutineContext)
    private var buffer = Buffer()
    private val flushes = Channel<Buffer>(Channel.BUFFERED)
    private val flushJob = scope.launch {
        try {
            for (b in flushes) {
                b.transferTo(channel.writeBuffer)
                channel.flush()
            }
        } catch (throwable: Throwable) {
            flushes.close(throwable)
        }
    }

    override fun write(b: Int) {
        assertNotClosed()
        buffer.writeByte(b.toByte())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        assertNotClosed()
        buffer.write(b, off, off + len)
    }

    override fun flush() {
        if (channel.isClosedForWrite || flushes.isClosedForSend) {
            throw channel.closedCause ?: ClosedWriteChannelException()
        }
        if (flushes.trySend(buffer).isFailure) {
            runBlocking(scope.coroutineContext) {
                flushes.send(buffer)
            }
        }
        buffer = Buffer()
    }

    override fun close() {
        runBlocking(scope.coroutineContext) {
            closeSuspend()
        }
    }

    public suspend fun closeSuspend() {
        flushes.trySend(buffer)
        flushes.close()
        flushJob.join()
    }

    private fun assertNotClosed() {
        if (flushes.isClosedForSend || channel.isClosedForWrite) {
            throw channel.closedCause ?: ClosedWriteChannelException()
        }
    }
}
