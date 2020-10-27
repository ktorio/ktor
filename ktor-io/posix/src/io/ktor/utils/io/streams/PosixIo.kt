@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.internal.utils.*
import platform.posix.*

@ExperimentalIoApi
public inline fun <R> CPointer<FILE>.use(block: (CPointer<FILE>) -> R): R {
    return try {
        block(this)
    } finally {
        fclose(this) // TODO handle errors
    }
}

@Deprecated("Use fwrite(Memory) instead.")
public fun fwrite(buffer: Buffer, stream: CPointer<FILE>): size_t {
    var written: size_t

    buffer.readDirect { pointer ->
        val result = fwrite(pointer, 1.convert(), buffer.readRemaining.convert(), stream)
        written = result

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        result.convert()
    }

    return written
}

@ExperimentalIoApi
public fun fwrite(source: Memory, offset: Int, length: Int, stream: CPointer<FILE>): Int {
    return fwrite(source, offset.toLong(), length.toLong(), stream).toInt()
}

@ExperimentalIoApi
public fun fwrite(source: Memory, offset: Long, length: Long, stream: CPointer<FILE>): Long {
    val maxLength = minOf<Long>(length, ssize_t.MAX_VALUE.convert())
    return fwrite(source.pointer + offset, 1.convert(), maxLength.convert(), stream).convert()
}

@Deprecated("Use write(Memory) instead.")
public fun write(fildes: Int, buffer: Buffer): ssize_t {
    var written: ssize_t

    buffer.readDirect { pointer ->
        val result = write(fildes, pointer, buffer.readRemaining.convert())
        written = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return written
}

@ExperimentalIoApi
public fun write(fildes: Int, source: Memory, offset: Int, length: Int): Int {
    return write(fildes, source, offset.toLong(), length.toLong()).toInt()
}

@ExperimentalIoApi
public fun write(fildes: Int, source: Memory, offset: Long, length: Long): Long {
    val maxLength = minOf<Long>(length, ssize_t.MAX_VALUE.convert())
    return write(fildes, source.pointer + offset, maxLength.convert()).convert()
}

@Deprecated("Use send(Memory) instead.")
public fun send(socket: KX_SOCKET, buffer: Buffer, flags: Int): ssize_t {
    var written: ssize_t

    buffer.readDirect { pointer ->
        val result = send(socket, pointer, buffer.readRemaining.convert(), flags)
        written = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return written
}

@ExperimentalIoApi
public fun send(socket: KX_SOCKET, source: Memory, sourceOffset: Long, maxLength: Long, flags: Int): Long {
    val pointer = source.pointer + sourceOffset

    return send(socket, pointer, maxLength.coerceAtMost(size_t.MAX_VALUE.toLong()).convert(), flags).convert()
}

@ExperimentalIoApi
public fun send(socket: KX_SOCKET, source: Memory, sourceOffset: Int, maxLength: Int, flags: Int): Int {
    return send(socket, source, sourceOffset.toLong(), maxLength.toLong(), flags).toInt()
}

@Deprecated("Use fread(Memory) instead.")
public fun fread(buffer: Buffer, stream: CPointer<FILE>): size_t {
    var bytesRead: size_t

    buffer.writeDirect { pointer ->
        val result = fread(pointer, 1.convert(), buffer.writeRemaining.convert(), stream)
        bytesRead = result

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        result.convert()
    }

    return bytesRead
}

@ExperimentalIoApi
public fun fread(destination: Memory, offset: Int, length: Int, stream: CPointer<FILE>): Int {
    return fread(destination, offset.toLong(), length.toLong(), stream).toInt()
}

@ExperimentalIoApi
public fun fread(destination: Memory, offset: Long, length: Long, stream: CPointer<FILE>): Long {
    val maxLength = minOf(length, Int.MAX_VALUE.toLong(), ssize_t.MAX_VALUE.toLong())
    val pointer = destination.pointer + offset

    val result = fread(pointer, 1.convert(), maxLength.convert(), stream)

    // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
    return result.convert()
}


@Deprecated("Use read(Memory) instead.")
public fun read(fildes: Int, buffer: Buffer): ssize_t {
    var bytesRead: ssize_t

    buffer.writeDirect { pointer ->
        val size = minOf(
            ssize_t.MAX_VALUE.toULong(),
            SSIZE_MAX.toULong(),
            buffer.writeRemaining.toULong()
        ).convert<size_t>()

        val result = read(fildes, pointer, size.convert())
        bytesRead = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return bytesRead
}

@ExperimentalIoApi
public fun read(fildes: Int, destination: Memory, offset: Int, length: Int): Int {
    return read(fildes, destination, offset.toLong(), length.toLong()).toInt()
}

@ExperimentalIoApi
public fun read(fildes: Int, destination: Memory, offset: Long, length: Long): Long {

    val maxLength = minOf<Long>(
        ssize_t.MAX_VALUE.convert(),
        length
    )

    return read(fildes, destination.pointer + offset, maxLength.convert()).convert<Long>().coerceAtLeast(0)
}

@Deprecated("Use fread(Memory) instead.")
public fun recv(socket: KX_SOCKET, buffer: Buffer, flags: Int): ssize_t {
    var bytesRead: ssize_t

    buffer.writeDirect { pointer ->
        val result = recv(socket, pointer, buffer.writeRemaining.convert(), flags)
        bytesRead = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return bytesRead
}

@ExperimentalIoApi
public fun recvfrom(
    socket: KX_SOCKET,
    buffer: Buffer,
    flags: Int,
    addr: CValuesRef<sockaddr>,
    addr_len: CValuesRef<KX_SOCKADDR_LENVar>
): ssize_t {
    var bytesRead: ssize_t

    buffer.writeDirect { pointer ->
        val result = recvfrom(socket, pointer, buffer.writeRemaining.convert(), flags, addr, addr_len)
        bytesRead = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return bytesRead
}

@ExperimentalIoApi
public fun sendto(
    socket: KX_SOCKET, buffer: Buffer, flags: Int,
    addr: CValuesRef<sockaddr>,
    addr_len: KX_SOCKADDR_LEN
): ssize_t {
    var written: ssize_t

    buffer.readDirect { pointer ->
        val result = sendto(socket, pointer, buffer.readRemaining.convert(), flags, addr, addr_len)
        written = result.convert()

        // it is completely safe to convert since the returned value will be never greater than Int.MAX_VALUE
        // however the returned value could be -1 so clamp it
        result.convert<Int>().coerceAtLeast(0)
    }

    return written
}
