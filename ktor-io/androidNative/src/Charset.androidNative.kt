/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.io.*

// iconv is Available since API level 28.
public actual object Charsets {
    public actual val UTF_8: Charset by lazy { CharsetImpl("UTF-8") }
    public actual val ISO_8859_1: Charset get() = error("not supported")
}

private class CharsetImpl(name: String) : Charset(name) {
    override fun newEncoder(): CharsetEncoder = CharsetEncoderImpl(this)
    override fun newDecoder(): CharsetDecoder = CharsetDecoderImpl(this)
}

internal actual fun findCharset(name: String): Charset {
    if (name == "UTF-8" || name == "utf-8" || name == "UTF8" || name == "utf8") return Charsets.UTF_8

    throw IllegalArgumentException("Charset $name is not supported")
}

internal actual fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Sink): Int {
    require(charset === Charsets.UTF_8) { "Only UTF-8 encoding is supported in AndroidNative" }
    val result = input.substring(fromIndex, toIndex).encodeToByteArray()
    dst.write(result)
    return result.size
}

@OptIn(InternalIoApi::class)
public actual fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int {
    require(charset === Charsets.UTF_8) { "Only UTF-8 encoding is supported in AndroidNative" }

    val count = minOf(input.buffer.size, max.toLong())
    val array = input.readByteArray(count.toInt())
    val result = try {
        array.decodeToString()
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
    dst.append(result)
    return result.length
}

internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray = buildPacket {
    encodeToImpl(this, input, fromIndex, toIndex)
}.readByteArray()
