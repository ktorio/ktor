/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import kotlinx.io.*
import org.khronos.webgl.*

private external class TextDecoder(encoding: String, options: TextDecoderOptions) : JsAny {
    fun decode(buffer: ArrayBufferView): String
}

private external interface TextDecoderOptions : JsAny {
    var fatal: Boolean
}

private fun fatalOptions(): TextDecoderOptions = js("({fatal: true})")

@OptIn(InternalIoApi::class)
public actual fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int {
    val decoder = TextDecoder(charset.name, fatalOptions())

    val count = minOf(input.buffer.size, max.toLong())
    val array = input.readByteArray(count.toInt())
    val result = try {
        decoder.decode(array.toInt8Array())
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
    dst.append(result)
    return result.length
}
