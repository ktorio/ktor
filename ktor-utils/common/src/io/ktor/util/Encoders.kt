/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.*
import io.ktor.utils.io.*

@KtorExperimentalAPI
/**
 * Empty [Encoder]
 */
public object Identity : Encoder {
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel = source

    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel = source
}

/**
 * Content encoder.
 */
@KtorExperimentalAPI
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
