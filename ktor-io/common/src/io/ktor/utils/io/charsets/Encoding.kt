package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

expect abstract class Charset {
    @ExperimentalIoApi
    abstract fun newEncoder(): CharsetEncoder

    @ExperimentalIoApi
    abstract fun newDecoder(): CharsetDecoder

    companion object {
        fun forName(name: String): Charset
    }
}

expect val Charset.name: String

// ----------------------------- ENCODER -------------------------------------------------------------------------------
@ExperimentalIoApi
expect abstract class CharsetEncoder

expect val CharsetEncoder.charset: Charset

@Deprecated(
    "Use writeText on Output instead.",
    ReplaceWith("dst.writeText(input, fromIndex, toIndex, charset)", "io.ktor.utils.io.core.writeText")
)
fun CharsetEncoder.encode(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Output) {
    encodeToImpl(dst, input, fromIndex, toIndex)
}

@ExperimentalIoApi
expect fun CharsetEncoder.encodeToByteArray(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray

@Deprecated(
    "Internal API. Will be hidden in future releases. Use encodeToByteArray instead.",
    replaceWith = ReplaceWith("encodeToByteArray(input, fromIndex, toIndex)")
)
fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray {
    return encodeToByteArray(input, fromIndex, toIndex)
}

@ExperimentalIoApi
expect fun CharsetEncoder.encodeUTF8(input: ByteReadPacket, dst: Output)

@ExperimentalIoApi
fun CharsetEncoder.encode(input: CharSequence, fromIndex: Int = 0, toIndex: Int = input.length) = buildPacket {
    encodeToImpl(this, input, fromIndex, toIndex)
}

@ExperimentalIoApi
fun CharsetEncoder.encodeUTF8(input: ByteReadPacket) = buildPacket {
    encodeUTF8(input, this)
}


@ExperimentalIoApi
fun CharsetEncoder.encode(input: CharArray, fromIndex: Int, toIndex: Int, dst: Output) {
    var start = fromIndex

    if (start >= toIndex) return
    dst.writeWhileSize(1) { view: Buffer ->
        val rc = encodeArrayImpl(input, start, toIndex, view)
        check(rc >= 0)
        start += rc

        when {
            start >= toIndex -> 0
            rc == 0 -> 8
            else -> 1
        }
    }

    encodeCompleteImpl(dst)
}


// ----------------------------- DECODER -------------------------------------------------------------------------------

@ExperimentalIoApi
expect abstract class CharsetDecoder

/**
 * Decoder's charset it is created for.
 */
expect val CharsetDecoder.charset: Charset

@ExperimentalIoApi
fun CharsetDecoder.decode(input: Input, max: Int = Int.MAX_VALUE): String =
    buildString(minOf(max.toLong(), input.sizeEstimate()).toInt()) {
        decode(input, this, max)
    }

@ExperimentalIoApi
expect fun CharsetDecoder.decode(input: Input, dst: Appendable, max: Int): Int

@ExperimentalIoApi
expect fun CharsetDecoder.decodeExactBytes(input: Input, inputLength: Int): String

// ----------------------------- REGISTRY ------------------------------------------------------------------------------
expect object Charsets {
    val UTF_8: Charset
    val ISO_8859_1: Charset
}

expect class MalformedInputException(message: String) : Throwable


// ----------------------------- INTERNALS -----------------------------------------------------------------------------

internal fun CharsetEncoder.encodeArrayImpl(input: CharArray, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    val length = toIndex - fromIndex
    return encodeImpl(CharArraySequence(input, fromIndex, length), 0, length, dst)
}

internal expect fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int

internal expect fun CharsetEncoder.encodeComplete(dst: Buffer): Boolean

internal expect fun CharsetDecoder.decodeBuffer(
    input: Buffer,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int = Int.MAX_VALUE
): Int

internal fun CharsetEncoder.encodeToByteArrayImpl1(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray {
    var start = fromIndex
    if (start >= toIndex) return EmptyByteArray
    val single = ChunkBuffer.Pool.borrow()

    try {
        val rc = encodeImpl(input, start, toIndex, single)
        start += rc
        if (start == toIndex) {
            val result = ByteArray(single.readRemaining)
            single.readFully(result)
            return result
        }

        return buildPacket {
            appendSingleChunk(single.duplicate())
            encodeToImpl(this, input, start, toIndex)
        }.readBytes()
    } finally {
        single.release(ChunkBuffer.Pool)
    }
}

internal fun Input.sizeEstimate(): Long = when (this) {
    is ByteReadPacket -> remaining
    is AbstractInput -> maxOf(remaining, 16)
    else -> 16
}


private fun CharsetEncoder.encodeCompleteImpl(dst: Output): Int {
    var size = 1
    var bytesWritten = 0

    dst.writeWhile { view ->
        val before = view.writeRemaining
        if (encodeComplete(view)) {
            size = 0
        } else {
            size++
        }
        bytesWritten += before - view.writeRemaining
        size > 0
    }

    return bytesWritten
}

internal fun CharsetEncoder.encodeToImpl(
    destination: Output,
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): Int {
    var start = fromIndex
    if (start >= toIndex) return 0

    var bytesWritten = 0

    destination.writeWhileSize(1) { view: Buffer ->
        val before = view.writeRemaining
        val rc = encodeImpl(input, start, toIndex, view)
        check(rc >= 0)
        start += rc
        bytesWritten += before - view.writeRemaining

        when {
            start >= toIndex -> 0
            rc == 0 -> 8
            else -> 1
        }
    }

    bytesWritten += encodeCompleteImpl(destination)
    return bytesWritten
}
