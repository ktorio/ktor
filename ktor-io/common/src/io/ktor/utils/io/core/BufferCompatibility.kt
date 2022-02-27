@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * Write byte [value] repeated the specified [times].
 */
public fun Buffer.fill(times: Int, value: Byte) {
    require(times >= 0) { "times shouldn't be negative: $times" }
    require(times <= writeRemaining) {
        "times shouldn't be greater than the write remaining space: $times > $writeRemaining"
    }

    memory.fill(writePosition, times, value)
    commitWritten(times)
}

/**
 * Write unsigned byte [value] repeated the specified [times].
 */
public fun Buffer.fill(times: Int, value: UByte) {
    fill(times, value.toByte())
}

/**
 * Write byte [v] value repeated [n] times.
 */
@Deprecated("Use fill with n with type Int")
public fun Buffer.fill(n: Long, v: Byte) {
    fill(n.toIntOrFail("n"), v)
}

/**
 * Push back [n] bytes: only possible if there were at least [n] bytes read before this operation.
 */
@Deprecated(
    "Use rewind instead",
    ReplaceWith("rewind(n)"),
    level = DeprecationLevel.ERROR
)
public fun Buffer.pushBack(n: Int): Unit = rewind(n)

@Deprecated(
    "Use duplicate instead",
    ReplaceWith("duplicate()"),
    level = DeprecationLevel.ERROR
)
public fun Buffer.makeView(): Buffer = duplicate()

@Deprecated(
    "Use duplicate instead",
    ReplaceWith("duplicate()"),
    level = DeprecationLevel.ERROR
)
public fun ChunkBuffer.makeView(): ChunkBuffer = duplicate()

@Deprecated(
    "Does nothing.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.flush() {
}

internal fun Buffer.appendChars(csq: CharSequence, start: Int = 0, end: Int = csq.length): Int {
    var charactersWritten: Int

    write { dst, dstStart, dstEndExclusive ->
        val result = dst.encodeUTF8(csq, start, end, dstStart, dstEndExclusive)
        charactersWritten = result.characters.toInt()
        result.bytes.toInt()
    }

    return start + charactersWritten
}

@Deprecated(
    "This is no longer supported. Use a packet builder to append characters instead.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.append(c: Char): Buffer {
    write { memory, start, endExclusive ->
        val size = memory.putUtf8Char(start, c.code)
        when {
            size > endExclusive - start -> appendFailed(1)
            else -> size
        }
    }

    return this
}

@Deprecated(
    "This is no longer supported. Use a packet builder to append characters instead.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.append(csq: CharSequence?): Buffer {
    error("This is no longer supported. Use a packet builder to append characters instead.")
}

@Deprecated(
    "This is no longer supported. Use a packet builder to append characters instead.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.append(csq: CharSequence?, start: Int, end: Int): Buffer = apply {
    error("This is no longer supported. Use a packet builder to append characters instead.")
}

private fun appendFailed(length: Int): Nothing {
    throw BufferLimitExceededException("Not enough free space available to write $length character(s).")
}

@Deprecated(
    "This is no longer supported. Use a packet builder to append characters instead.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.append(csq: CharArray, start: Int, end: Int): Buffer {
    error("This is no longer supported. Use a packet builder to append characters instead.")
}

@Deprecated(
    "This is no longer supported. Read from a packet instead.",
    level = DeprecationLevel.ERROR
)
public fun Buffer.readText(
    decoder: CharsetDecoder,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int = Int.MAX_VALUE
): Int {
    return decoder.decodeBuffer(this, out, lastBuffer, max)
}

/**
 * Peek the next unsigned byte or return `-1` if no more bytes available for reading. No bytes will be marked
 * as consumed in any case.
 * @see [Buffer.tryPeekByte]
 */
@Deprecated(
    "Use tryPeekByte instead",
    replaceWith = ReplaceWith("tryPeekByte()"),
    level = DeprecationLevel.ERROR
)
public fun Buffer.tryPeek(): Int = tryPeekByte()

public fun Buffer.readFully(dst: Array<Byte>, offset: Int = 0, length: Int = dst.size - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        for (index in 0 until length) {
            dst[index + offset] = memory[index + start]
        }

        length
    }
}
