/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*

/**
 * Find a charset by name.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.charsets.forName)
 */
public actual fun Charsets.forName(name: String): Charset = Charset.forName(name)

/**
 * Check if a charset is supported by the current platform.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.charsets.isSupported)
 */
public actual fun Charsets.isSupported(name: String): Boolean = Charset.isSupported(name)

public actual abstract class Charset(internal val _name: String) {
    public actual abstract fun newEncoder(): CharsetEncoder
    public actual abstract fun newDecoder(): CharsetDecoder

    public companion object {
        public fun forName(name: String): Charset = findCharset(name)

        public fun isSupported(charset: String): Boolean = when (charset) {
            "UTF-8", "utf-8", "UTF8", "utf8" -> true
            "ISO-8859-1", "iso-8859-1" -> true
            "UTF-16", "utf-16", "UTF16", "utf16" -> true
            else -> false
        }
    }

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Charset) return false
        return _name == other._name
    }

    actual override fun hashCode(): Int {
        return _name.hashCode()
    }

    actual override fun toString(): String {
        return _name
    }
}

internal expect fun findCharset(name: String): Charset

public actual val Charset.name: String get() = _name

// -----------------------

public actual abstract class CharsetEncoder(internal val _charset: Charset)
internal data class CharsetEncoderImpl(private val charset: Charset) : CharsetEncoder(charset)

public actual val CharsetEncoder.charset: Charset get() = _charset

public actual fun CharsetEncoder.encodeToByteArray(input: CharSequence, fromIndex: Int, toIndex: Int): ByteArray =
    encodeToByteArrayImpl(input, fromIndex, toIndex)

// ----------------------------------------------------------------------

public actual abstract class CharsetDecoder(internal val _charset: Charset)
internal data class CharsetDecoderImpl(private val charset: Charset) : CharsetDecoder(charset)

public actual val CharsetDecoder.charset: Charset get() = _charset

internal val platformUtf16: String =
    if (ByteOrder.nativeOrder() == io.ktor.utils.io.core.ByteOrder.BIG_ENDIAN) "UTF-16BE" else "UTF-16LE"

// -----------------------------------------------------------
public actual open class MalformedInputException actual constructor(message: String) : kotlinx.io.IOException(message)
