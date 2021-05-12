package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import java.nio.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Read at most `dst.remaining()` bytes to the specified [dst] byte buffer and change it's position accordingly
 * @return number of bytes copied
 */
public fun ByteReadPacket.readAvailable(dst: ByteBuffer): Int = readAsMuchAsPossible(dst, 0)

/**
 * Read exactly `dst.remaining()` bytes to the specified [dst] byte buffer and change it's position accordingly
 * @return number of bytes copied
 */
public fun ByteReadPacket.readFully(dst: ByteBuffer): Int {
    val rc = readAsMuchAsPossible(dst, 0)
    if (dst.hasRemaining()) {
        throw EOFException("Not enough data in packet to fill buffer: ${dst.remaining()} more bytes required")
    }
    return rc
}

private tailrec fun ByteReadPacket.readAsMuchAsPossible(bb: ByteBuffer, copied: Int): Int {
    if (!bb.hasRemaining()) return copied
    val current: ChunkBuffer = prepareRead(1) ?: return copied

    val destinationCapacity = bb.remaining()
    val available = current.readRemaining

    return if (destinationCapacity >= available) {
        current.readFully(bb, available)
        releaseHead(current)

        readAsMuchAsPossible(bb, copied + available)
    } else {
        current.readFully(bb, destinationCapacity)
        headPosition = current.readPosition
        copied + destinationCapacity
    }
}

/**
 * Write bytes directly to packet builder's segment. Generally shouldn't be used in user's code and useful for
 * efficient integration.
 *
 * Invokes [block] lambda with one byte buffer. [block] lambda should change provided's position accordingly but
 * shouldn't change any other pointers.
 *
 * @param size minimal number of bytes should be available in a buffer provided to the lambda. Should be as small as
 * possible. If [size] is too large then the function may fail because the segments size is not guaranteed to be fixed
 * and not guaranteed that is will be big enough to keep [size] bytes. However it is guaranteed that the segment size
 * is at least 8 bytes long (long integer bytes length)
 */
public inline fun BytePacketBuilder.writeDirect(size: Int, block: (ByteBuffer) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    writeByteBufferDirect(size, block)
}

/**
 * Write bytes directly to packet builder's segment. Generally shouldn't be used in user's code and useful for
 * efficient integration.
 *
 * Invokes [block] lambda with one byte buffer. [block] lambda should change provided's position accordingly but
 * shouldn't change any other pointers.
 *
 * @param size minimal number of bytes should be available in a buffer provided to the lambda. Should be as small as
 * possible. If [size] is too large then the function may fail because the segments size is not guaranteed to be fixed
 * and not guaranteed that is will be big enough to keep [size] bytes. However it is guaranteed that the segment size
 * is at least 8 bytes long (long integer bytes length)
 */
public inline fun BytePacketBuilder.writeByteBufferDirect(size: Int, block: (ByteBuffer) -> Unit): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write(size) {
        it.writeDirect(size, block)
    }
}

public inline fun ByteReadPacket.readDirect(size: Int, block: (ByteBuffer) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    read(size) {
        it.readDirect(block)
    }
}

@Suppress("DEPRECATION")
@Deprecated("Use read {} instead.")
public inline fun Input.readDirect(size: Int, block: (ByteBuffer) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    read(size) { view ->
        view.readDirect {
            block(it)
        }
    }
}

/**
 * Write all [src] buffer remaining bytes and change it's position accordingly
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Should be resolved to member function instead", level = DeprecationLevel.HIDDEN)
public fun BytePacketBuilder.writeFully(src: ByteBuffer) {
    writeFully(src)
}

@PublishedApi
@Suppress("unused", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun BytePacketBuilder.nioBuffer(size: Int): ByteBuffer = prepareWriteHead(size).writeBuffer()

@PublishedApi
@Suppress("unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun BytePacketBuilder.afterNioBufferUsed(written: Int) {
    head.commitWritten(written)
    afterHeadWrite()
}

@PublishedApi
@Suppress("unused", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun ByteReadPacket.nioBuffer(size: Int): ByteBuffer? {
    return prepareRead(size)?.writeBuffer()
}

@PublishedApi
@Suppress("unused", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun Input.nioBuffer(size: Int): ByteBuffer? {
    return prepareRead(size)?.writeBuffer()
}

@PublishedApi
@Suppress("unused", "DEPRECATION", "DEPRECATION_ERROR")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun ByteReadPacket.afterNioBufferUsed(read: Int) {
    afterNioBufferUsed(read)
}

@PublishedApi
@Suppress("DEPRECATION_ERROR")
internal fun Input.afterNioBufferUsed(read: Int) {
    val headRemaining = headRemaining
    updateHeadRemaining(headRemaining - read)
}

internal fun Buffer.writeBuffer(): ByteBuffer {
    return memory.slice(writePosition, writeRemaining).buffer
}

internal fun Buffer.hasArray(): Boolean = memory.buffer.let { it.hasArray() && !it.isReadOnly }
