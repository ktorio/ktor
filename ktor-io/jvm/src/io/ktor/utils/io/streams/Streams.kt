/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.io.*

public fun InputStream.asInput(): Input = asSource().buffered()

@Suppress("DEPRECATION")
public fun ByteReadPacket.inputStream(): InputStream = asInputStream()

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun OutputStream.writePacket(packet: ByteReadPacket) {
    packet.buffer.copyTo(this)
}

@Suppress("DEPRECATION")
public fun OutputStream.writePacket(block: BytePacketBuilder.() -> Unit) {
    val builder = Buffer()
    builder.block()
    writePacket(builder)
}

@Suppress("DEPRECATION")
@OptIn(UnsafeIoApi::class)
public fun InputStream.readPacketAtLeast(min: Int = 1): ByteReadPacket {
    val buffer = Buffer()
    UnsafeBufferOperations.writeToTail(buffer, min) { array, start, end ->
        val read = read(array, start, end - start)
        if (read < 0) 0 else read
    }

    return buffer
}
