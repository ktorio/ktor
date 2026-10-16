/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.nio

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [ByteReadChannel] that reads from this channel on demand in the given [context].
 * Reads **block** the executing thread. Use a dispatcher suitable for blocking I/O
 * in [context], such as [Dispatchers.IO] used by default.
 *
 * The channel closes the stream at EOF or on cancellation.
 * The [ByteReadChannel.cancel] call returns without waiting for stream closure.
 *
 * The stream must support closure during an active read.
 * Cancellation and closure do not guarantee that a blocking read stops.
 *
 * ### Structured concurrency
 *
 * If [context] contains a [Job], it closes the stream on cancellation.
 * If that job is active when the channel is created, it also waits for stream closure before completing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.nio.toByteReadChannel)
 *
 * @param context the [CoroutineContext] to execute the read operation. Defaults to [Dispatchers.IO].
 * @return a [ByteReadChannel] that reads from the given [ReadableByteChannel] on demand.
 */
public fun ReadableByteChannel.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Converts a [ReadableByteChannel] into a [RawSource].
 *
 * This extension function wraps the given [ReadableByteChannel] into a [RawSource],
 * enabling efficient reading of bytes from the channel as a source of data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.nio.asSource)
 *
 * @return a [RawSource] representation of the [ReadableByteChannel].
 */
public fun ReadableByteChannel.asSource(): RawSource =
    ReadableByteChannelSource(this)

/**
 * A data source that reads from a [ReadableByteChannel].
 *
 * This class implements the [RawSource] interface, allowing for the reading
 * of bytes from a [ReadableByteChannel] into a [Buffer].
 *
 * @property channel The [ReadableByteChannel] from which bytes are read.
 */
private open class ReadableByteChannelSource(
    private val channel: ReadableByteChannel,
) : RawSource {
    @OptIn(UnsafeIoApi::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) return 0L

        var readTotal: Int
        val actualByteCount = minOf(byteCount, Int.MAX_VALUE.toLong()).toInt()

        UnsafeBufferOperations.writeToTail(sink, 1) { data, pos, limit ->
            val maxToCopy = minOf(actualByteCount, limit - pos)
            val buffer = ByteBuffer.wrap(data, pos, maxToCopy)
            readTotal = channel.read(buffer)
            maxOf(readTotal, 0)
        }

        return readTotal.toLong()
    }

    override fun close() =
        channel.close()

    override fun toString(): String =
        "ReadableByteChannelSource($channel)"
}
