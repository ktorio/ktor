/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import kotlinx.io.*

@OptIn(InternalIoApi::class)
public actual fun CharsetDecoder.decode(
    input: Source,
    dst: Appendable,
    max: Int
): Int {
    val count = minOf(input.buffer.size, max.toLong())
    val array = input.readByteArray(count.toInt())
    val decoded = try {
        when (charset) {
            Charsets.ISO_8859_1 -> decodeISO88591(array)
            Charsets.UTF_8 -> array.decodeToString()
            else -> error("Only UTF-8 and ISO_8859_1 encoding are supported in wasm-wasi")
        }
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
    dst.append(decoded)
    return decoded.length
}
