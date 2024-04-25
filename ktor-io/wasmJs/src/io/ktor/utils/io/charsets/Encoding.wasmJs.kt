/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import kotlinx.io.*

public actual abstract class Charset {
    public actual abstract fun newEncoder(): CharsetEncoder
    public actual abstract fun newDecoder(): CharsetDecoder
}

/**
 * Check if a charset is supported by the current platform.
 */
public actual fun Charsets.isSupported(name: String): Boolean {
    TODO("Not yet implemented")
}

/**
 * Find a charset by name.
 */
public actual fun Charsets.forName(name: String): Charset {
    TODO("Not yet implemented")
}

public actual val Charset.name: String
    get() = TODO("Not yet implemented")

// ----------------------------- ENCODER -------------------------------------------------------------------------------
public actual abstract class CharsetEncoder

public actual val CharsetEncoder.charset: Charset
    get() = TODO("Not yet implemented")

/**
 * Decoder's charset it is created for.
 */
public actual val CharsetDecoder.charset: Charset
    get() = TODO("Not yet implemented")

public actual fun CharsetEncoder.encodeToByteArray(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    TODO("Not yet implemented")
}

public actual abstract class CharsetDecoder

public actual fun CharsetDecoder.decode(
    input: Source,
    dst: Appendable,
    max: Int
): Int {
    TODO("Not yet implemented")
}

// ----------------------------- REGISTRY ------------------------------------------------------------------------------
public actual object Charsets {
    public actual val UTF_8: Charset
        get() = TODO("Not yet implemented")
    public actual val ISO_8859_1: Charset
        get() = TODO("Not yet implemented")
}

public actual open class MalformedInputException actual constructor(message: String) : Throwable()

internal actual fun CharsetEncoder.encodeImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int,
    dst: Sink
): Int {
    TODO("Not yet implemented")
}

internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    TODO("Not yet implemented")
}
