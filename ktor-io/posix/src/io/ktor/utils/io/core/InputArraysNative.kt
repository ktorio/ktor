package io.ktor.utils.io.core

import kotlinx.cinterop.*

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun Input.readFully(dst: CPointer<ByteVar>, offset: Int, length: Int) {
    if (readAvailable(dst, offset, length) != length) {
        prematureEndOfStream(length)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun Input.readFully(dst: CPointer<ByteVar>, offset: Long, length: Long) {
    if (readAvailable(dst, offset, length) != length) {
        prematureEndOfStream(length)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun Input.readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int {
    var bytesCopied = 0

    takeWhile { buffer ->
        val partSize = minOf(length - bytesCopied, buffer.readRemaining)
        buffer.readFully(dst, offset + bytesCopied, partSize)
        bytesCopied += partSize
        bytesCopied < length
    }

    return bytesCopied
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun Input.readAvailable(dst: CPointer<ByteVar>, offset: Long, length: Long): Long {
    var bytesCopied = 0L

    takeWhile { buffer ->
        val partSize = minOf(length - bytesCopied, buffer.readRemaining.toLong()).toInt()
        buffer.readFully(dst, offset + bytesCopied, partSize)
        bytesCopied += partSize
        bytesCopied < length
    }

    return bytesCopied
}
