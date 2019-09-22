/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.core.*
import java.io.*

/**
 * Convert io.ktor.utils.io [Input] to java [InputStream]
 */
@KtorExperimentalAPI
fun Input.asStream(): InputStream = object : InputStream() {

    override fun read(): Int = tryPeek()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (this@asStream.endOfInput) return -1
        return readAvailable(buffer, offset, length)
    }

    override fun skip(count: Long): Long = discard(count)

    override fun close() {
        this@asStream.close()
    }
}

