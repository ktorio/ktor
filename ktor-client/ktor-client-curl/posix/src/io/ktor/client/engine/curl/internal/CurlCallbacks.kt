package io.ktor.client.engine.curl.internal

import kotlinx.cinterop.*
import kotlinx.io.core.*
import platform.posix.*

internal fun onHeadersReceived(
    buffer: CPointer<ByteVar>,
    size: size_t, count: size_t,
    userdata: COpaquePointer
): Long = selectPacket(buffer, size, count, userdata) { it.headersBytes }

internal fun onBodyChunkReceived(
    buffer: CPointer<ByteVar>,
    size: size_t, count: size_t,
    userdata: COpaquePointer
): Long = selectPacket(buffer, size, count, userdata) { it.bodyBytes }

internal inline fun selectPacket(
    buffer: CPointer<ByteVar>,
    size: size_t,
    count: size_t,
    userData: COpaquePointer,
    block: (CurlResponseBuilder) -> BytePacketBuilder
): Long {
    val chunkSize = (size * count).toLong()
    val chunk = buffer.readBytes(chunkSize.toInt())
    block(userData.fromCPointer()).writeFully(chunk)
    return chunkSize
}

internal fun onBodyChunkRequested(
    buffer: CPointer<ByteVar>,
    size: size_t, count: size_t,
    dataRef: COpaquePointer
): Long {
    val streamRef = dataRef.asStableRef<ByteReadPacket>()
    val body = streamRef.get()
    val requested = (size * count).toLong()

    if (body.isEmpty) {
        streamRef.dispose()
        return 0
    }

    val readCount = minOf(body.remaining, requested)
    val chunk = body.readBytes(readCount.toInt())
    chunk.copyToBuffer(buffer, readCount.toULong())
    return readCount
}
