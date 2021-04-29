/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Empty [Encoder] that doesn't do any changes.
 */
public object Identity : Encoder {
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel = source

    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel = source
}

/**
 * Content encoder.
 */
public interface Encoder {
    /**
     * Launch coroutine to encode [source] bytes.
     */
    public fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel

    /**
     * Launch coroutine to decode [source] bytes.
     */
    public fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel
}
