/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import java.io.*
import kotlin.math.*

/**
 * Create blocking [java.io.InputStream] for this channel that does block every time the channel suspends at read
 * Similar to do reading in [runBlocking] however you can pass it to regular blocking API
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toInputStream)
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(InternalAPI::class)
public fun ByteReadChannel.toInputStream(parent: Job? = null): InputStream = object : InputStream() {

    override fun read(): Int {
        if (isClosedForRead) return -1
        if (readBuffer.exhausted()) blockingWait()

        if (isClosedForRead) return -1
        return readBuffer.readByte().toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isClosedForRead) return -1
        if (readBuffer.exhausted()) blockingWait()

        val count = min(availableForRead, len)
        val result = readBuffer.readAtMostTo(b, off, off + count)
        if (result >= 0) return result
        return if (isClosedForRead) -1 else 0
    }

    private fun blockingWait() {
        runBlocking {
            awaitContent()
        }
    }

    override fun close() {
        cancel()
    }
}

/**
 * Create blocking [java.io.OutputStream] for this channel that does block every time the channel suspends at write
 * Similar to do reading in [runBlocking] however you can pass it to regular blocking API
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toOutputStream)
 */
public fun ByteWriteChannel.toOutputStream(): OutputStream =
    ByteChannelOutputStream(this)

private const val FLUSH_THRESHOLD: Long = CHANNEL_MAX_SIZE.toLong()

internal class ByteChannelOutputStream(
    private val channel: ByteWriteChannel
) : OutputStream() {
    @OptIn(InternalAPI::class)
    private val channelWriteBuffer = channel.writeBuffer

    override fun write(b: Int) {
        throwIfClosed()
        channelWriteBuffer.writeByte(b.toByte())
        flushIfNeeded()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        throwIfClosed()
        channelWriteBuffer.write(b, off, off + len)
        flushIfNeeded()
    }

    @OptIn(InternalIoApi::class)
    private fun flushIfNeeded() {
        if (channelWriteBuffer.buffer.size >= FLUSH_THRESHOLD) {
            flush()
        }
    }

    override fun flush() {
        throwIfClosed()
        runBlocking {
            channel.flush()
        }
    }

    private fun throwIfClosed() {
        if (channel.isClosedForWrite) throw channel.closedCause ?: ClosedWriteChannelException()
    }

    override fun close() {
        runBlocking {
            channel.flushAndClose()
        }
    }
}
