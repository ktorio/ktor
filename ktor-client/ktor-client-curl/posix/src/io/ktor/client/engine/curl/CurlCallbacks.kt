package io.ktor.client.engine.curl

import kotlinx.cinterop.*
import platform.posix.*

internal fun headerCallback(buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer?): Long {
    if (buffer == null) return 0L
    val responseData = userdata!!.fromCPointer<CurlResponseData>()
    responseData.headers.add(buffer.readBytes((size * nitems).toInt()))
    return (size * nitems).toLong()
}

// TODO: Rather than waiting for request completion to pick all the data downloaded
// consider returning available chunks on each worker iteration.
internal fun writeCallback(buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer?): Long {
    val space = size * nitems
    val responseData = userdata!!.fromCPointer<CurlResponseData>()

    buffer?.let { responseData.chunks.add(it.readBytes(space.toInt())) } ?: return 0L

    return space.toLong()
}

// TODO: Use BytePacket.
internal class SimpleByteArrayStream(
    val data: ByteArray,
    var position: Int = 0
)

internal fun readCallback(buffer: CPointer<ByteVar>, size: size_t, nitems: size_t, dataRef: COpaquePointer?): Long {
    val streamRef = dataRef!!.asStableRef<SimpleByteArrayStream>()
    val stream = streamRef.get()
    val requested = (size * nitems).toLong()

    if (stream.position == stream.data.size) {
        streamRef.dispose()
        return 0
    }

    val consumed = minOf(stream.data.size - stream.position, requested.toInt())

    stream.data.copyToBuffer(buffer, consumed.toULong(), stream.position)
    stream.position += consumed

    return consumed.toLong()
}
