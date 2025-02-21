/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.asByteWriteChannel
import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.io.*

public fun InputStream.asInput(): Input = asSource().buffered()

public fun Source.inputStream(): InputStream = asInputStream()

@OptIn(InternalIoApi::class)
public fun OutputStream.writePacket(packet: Source) {
    packet.transferTo(this.asSink())
}

public fun OutputStream.writePacket(block: Sink.() -> Unit) {
    val builder = Buffer()
    builder.block()
    writePacket(builder)
}

@OptIn(UnsafeIoApi::class)
public fun InputStream.readPacketAtLeast(min: Int = 1): Source {
    val buffer = Buffer()
    UnsafeBufferOperations.writeToTail(buffer, min) { array, start, end ->
        val read = read(array, start, end - start)
        if (read < 0) 0 else read
    }

    return buffer
}

/**
 * Converts this [OutputStream] into a [ByteWriteChannel], enabling asynchronous writing of byte sequences.
 *
 * ```kotlin
 * val outputStream: OutputStream = FileOutputStream("file.txt")
 * val channel: ByteWriteChannel = outputStream.asByteWriteChannel()
 * channel.writeFully("Hello, World!".toByteArray())
 * channel.flushAndClose() // Ensure the data is written to the OutputStream
 * ```
 *
 * All operations on the [ByteWriteChannel] are buffered: the underlying [OutputStream] will be receiving bytes
 * when the [ByteWriteChannel.flush] happens.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.streams.asByteWriteChannel)
 */
public fun OutputStream.asByteWriteChannel(): ByteWriteChannel = asSink().asByteWriteChannel()
