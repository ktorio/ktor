/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import io.ktor.utils.io.js.*

// I don't know any characters that have longer characters
internal const val MAX_CHARACTERS_SIZE_IN_BYTES: Int = 8
private const val MAX_CHARACTERS_COUNT = Int.MAX_VALUE / MAX_CHARACTERS_SIZE_IN_BYTES

internal data class DecodeBufferResult(val charactersDecoded: String, val bytesConsumed: Int)

internal fun ByteArray.decodeBufferImpl(nativeDecoder: Decoder, maxCharacters: Int): DecodeBufferResult {
    if (maxCharacters == 0) {
        return DecodeBufferResult("", 0)
    }

    // fast-path: try to assume that we have 1 byte per character content
    try {
        val sizeInBytes = maxCharacters.coerceAtMost(size)
        val subarray = ByteArray(sizeInBytes) { this[it] }
        val text = nativeDecoder.decode(subarray)
        if (text.length <= maxCharacters) {
            return DecodeBufferResult(text, sizeInBytes)
        }
    } catch (_: Throwable) {
    }

    return decodeBufferImplSlow(nativeDecoder, maxCharacters)
}

private fun ByteArray.decodeBufferImplSlow(nativeDecoder: Decoder, maxCharacters: Int): DecodeBufferResult {
    val maxBytes = when {
        maxCharacters >= MAX_CHARACTERS_COUNT -> Int.MAX_VALUE
        else -> maxCharacters * MAX_CHARACTERS_SIZE_IN_BYTES
    }.coerceAtMost(size)

    var sizeInBytes = maxBytes
    while (sizeInBytes > MAX_CHARACTERS_SIZE_IN_BYTES) {
        try {
            val subarray = ByteArray(sizeInBytes.coerceAtMost(size)) { this[it] }
            val text = nativeDecoder.decode(subarray)
            if (text.length <= maxCharacters) {
                return DecodeBufferResult(text, sizeInBytes)
            }
        } catch (_: Throwable) {
        }

        sizeInBytes /= 2
    }

    sizeInBytes = MAX_CHARACTERS_SIZE_IN_BYTES
    while (sizeInBytes > 0) {
        try {
            val subarray = ByteArray(sizeInBytes.coerceAtMost(size)) { this[it] }
            val text = nativeDecoder.decode(subarray)
            if (text.length <= maxCharacters) {
                return DecodeBufferResult(text, sizeInBytes)
            }
        } catch (_: Throwable) {
        }

        sizeInBytes--
    }

    // all attempts were failed so most likely we have a broken character but we can't find it for some reason
    // so the following decode most likely will fail
    decodeWrap {
        nativeDecoder.decode(this)
    }

    // if it didn't for some reason we have no idea what to do
    throw MalformedInputException("Unable to decode buffer")
}
